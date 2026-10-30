#include "qsshclient_p.h"
#include "qsshforwardstream_p.h"
#include "qsshsession_p.h"
#include "qsshtunnel_p.h"

#include <QPointer>
#include <QThread>
#include <algorithm>

namespace {
    /** 首次启动 task 前检查 token，避免 Private 在 co_spawn 排队期间已被删除。 */
    asio::awaitable<void> runIfAlive(std::shared_ptr<bool> token, asio::awaitable<void> task) {
        if (*token) {
            co_await std::move(task);
        }
    }
} // namespace

QSshTunnelPrivate::QSshTunnelPrivate(QSshTunnel *owner, QSshSession *connection)
    : q(owner),
      session(connection->d_func()),
      acceptor(session->client->io),
      deadline(session->client->io) {
    session->client->synchronize([this] { session->tunnels.push_back(this); });
}

QSshTunnelPrivate::~QSshTunnelPrivate() {
    session->client->synchronize([this] {
        shutdown();
        *alive = false;
        std::erase(session->tunnels, this);
    });
}

void QSshTunnelPrivate::submit(std::function<void()> task) {
    asio::post(session->client->io, [token = alive, task = std::move(task)] {
        if (*token) {
            task();
        }
    });
}

void QSshTunnelPrivate::setState(QSshTunnel::State value, quint16 port) {
    phase = value;
    QMetaObject::invokeMethod(
        q,
        [this, value, port] {
            if (frontState == value || (frontClosing && value != QSshTunnel::Closing && value != QSshTunnel::Closed)) {
                return;
            }
            frontState = value;
            if (value == QSshTunnel::Listening) {
                frontPort = port;
            }
            QPointer<QSshTunnel> guard(q);
            Q_EMIT q->stateChanged(value);
            if (!guard) {
                return;
            }
            if (value == QSshTunnel::Listening && !frontClosing) {
                Q_EMIT q->listening();
            } else if (value == QSshTunnel::Closed) {
                Q_EMIT q->closed();
            }
        },
        Qt::QueuedConnection);
}

void QSshTunnelPrivate::fail(QSshTunnel::Error error, const QString &message, bool fatal) {
    QMetaObject::invokeMethod(
        q,
        [this, error, message] {
            frontError = error;
            frontErrorText = message;
            Q_EMIT q->errorOccurred(error);
        },
        Qt::QueuedConnection);
    if (fatal) {
        shutdown();
    }
}

void QSshTunnelPrivate::start(QSshTunnel::Options configuration) {
    if (phase != QSshTunnel::Idle) {
        return;
    }
    options = std::move(configuration);
    if (session->phase != QSshSession::Ready || session->terminated) {
        fail(QSshTunnel::InvalidConfiguration, QStringLiteral("Tunnel requires a ready SSH session"));
        return;
    }
    setState(QSshTunnel::Opening);
    if (options.type == QSshTunnel::Remote) {
        deadline.expires_after(std::chrono::milliseconds(session->options.channelTimeoutMs));
        deadline.async_wait([this, token = alive](const asio::error_code &error) {
            if (*token && !error) {
                fail(QSshTunnel::TimeoutError, QStringLiteral("Remote forwarding request timed out"));
            }
        });
        // 请求记录与 Qt 对象分离，删除或超时后到达的成功响应仍会被自动撤销。
        session->requestRemote(
            false, options.listenHost, options.listenPort,
            [this, token = alive, owner = session, address = options.listenHost](int result, int port) {
                if (!*token || phase != QSshTunnel::Opening) {
                    if (result == SSH_OK) {
                        owner->requestRemote(true, address, port, [](int, int) {});
                    }
                    if (*token && phase == QSshTunnel::Closing) {
                        setState(QSshTunnel::Closed);
                    }
                    return;
                }
                deadline.cancel();
                if (result != SSH_OK) {
                    fail(QSshTunnel::RequestRejected, QStringLiteral("Remote listener rejected"));
                    return;
                }
                boundPort = port;
                setState(QSshTunnel::Listening, boundPort);
            });
        return;
    }
    asio::error_code error;
    const auto address = asio::ip::make_address(options.listenHost.toStdString(), error);
    if (error) {
        fail(QSshTunnel::InvalidConfiguration, QStringLiteral("Local listenHost must be an IP address"));
        return;
    }
    const asio::ip::tcp::endpoint endpoint(address, options.listenPort);
    acceptor.open(endpoint.protocol(), error);
    if (!error) {
        acceptor.set_option(asio::socket_base::reuse_address(true), error);
    }
    if (!error) {
        acceptor.bind(endpoint, error);
    }
    if (!error) {
        acceptor.listen(asio::socket_base::max_listen_connections, error);
    }
    if (error) {
        fail(QSshTunnel::ListenError, QString::fromStdString(error.message()));
        return;
    }
    boundPort = acceptor.local_endpoint().port();
    setState(QSshTunnel::Listening, boundPort);
    // 首次调度也检查令牌，保证 open() 后立即删除隧道不会启动悬空协程。
    asio::co_spawn(session->client->io, runIfAlive(alive, doAccept()), [this, token = alive](std::exception_ptr error) {
        if (*token && error && phase == QSshTunnel::Listening) {
            fail(QSshTunnel::ListenError, QStringLiteral("Tunnel accept failed"));
        }
    });
}

asio::awaitable<void> QSshTunnelPrivate::doAccept() {
    const auto token = alive;
    while (*token && phase == QSshTunnel::Listening) {
        // 返回 socket 的原生 accept 操作不保留 acceptor 的引用，可以在取消后安全销毁 Private。
        asio::error_code error;
        auto accepted = co_await acceptor.async_accept(asio::redirect_error(asio::use_awaitable, error));
        if (!*token || phase != QSshTunnel::Listening) {
            co_return;
        }
        if (error) {
            fail(QSshTunnel::ListenError, QString::fromStdString(error.message()));
            co_return;
        }
        if (session->streams.size() >= QSshSessionPrivate::MaximumForwardedConnections) {
            continue;
        }
        auto stream = std::make_shared<QSshForwardStream>(session);
        session->streams.push_back(stream);
        connections.push_back(stream);
        stream->onError = [this, token](const QString &message) {
            if (*token) {
                fail(QSshTunnel::ConnectionError, message, false);
            }
        };
        std::erase_if(connections, [](const auto &value) { return value.expired(); });
        stream->startOutgoing(std::move(accepted), options.destinationHost, options.destinationPort,
                              options.type == QSshTunnel::Dynamic);
    }
}

void QSshTunnelPrivate::acceptRemote(ssh_channel channel) {
    auto stream = std::make_shared<QSshForwardStream>(session, channel);
    session->streams.push_back(stream);
    connections.push_back(stream);
    stream->onError = [this, token = alive](const QString &message) {
        if (*token) {
            fail(QSshTunnel::ConnectionError, message, false);
        }
    };
    std::erase_if(connections, [](const auto &value) { return value.expired(); });
    stream->startIncoming(QSshForwardStream::Tcp, options.destinationHost, options.destinationPort);
}

void QSshTunnelPrivate::shutdown(bool sessionClosed) {
    if (phase == QSshTunnel::Closed) {
        return;
    }
    if (phase == QSshTunnel::Closing) {
        if (sessionClosed) {
            deadline.cancel();
            setState(QSshTunnel::Closed);
        }
        return;
    }
    setState(QSshTunnel::Closing);
    deadline.cancel();
    asio::error_code ignored;
    acceptor.close(ignored);
    for (const auto &entry : connections) {
        if (auto stream = entry.lock()) {
            stream->close();
        }
    }
    connections.clear();
    if (!sessionClosed && !session->terminated && options.type == QSshTunnel::Remote && boundPort) {
        const auto token = alive;
        session->requestRemote(true, options.listenHost, boundPort, [this, token](int result, int) {
            if (!*token || phase == QSshTunnel::Closed) {
                return;
            }
            deadline.cancel();
            if (result != SSH_OK) {
                fail(QSshTunnel::RequestRejected, QStringLiteral("Remote listener cancellation rejected"), false);
            }
            setState(QSshTunnel::Closed);
        });
        deadline.expires_after(std::chrono::milliseconds(session->options.channelTimeoutMs));
        deadline.async_wait([this, token](const asio::error_code &error) {
            if (*token && !error && phase != QSshTunnel::Closed) {
                fail(QSshTunnel::TimeoutError, QStringLiteral("Remote listener cancellation timed out"), false);
                setState(QSshTunnel::Closed);
            }
        });
    } else {
        setState(QSshTunnel::Closed);
    }
}

QSshTunnel::QSshTunnel(QSshSession *session) : QObject(session), d_ptr(new QSshTunnelPrivate(this, session)) {
    qRegisterMetaType<State>();
    qRegisterMetaType<Error>();
}

QSshTunnel::~QSshTunnel() {
    Q_ASSERT(thread() == QThread::currentThread());
}

QSshTunnel::State QSshTunnel::state() const {
    Q_D(const QSshTunnel);
    return d->frontState;
}

QSshTunnel::Error QSshTunnel::error() const {
    Q_D(const QSshTunnel);
    return d->frontError;
}

QString QSshTunnel::errorString() const {
    Q_D(const QSshTunnel);
    return d->frontErrorText;
}

quint16 QSshTunnel::boundPort() const {
    Q_D(const QSshTunnel);
    return d->frontPort;
}

void QSshTunnel::open(const Options &options) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshTunnel);
    if (d->frontState != Idle || d->frontClosing) {
        return;
    }
    d->frontState = Opening;
    d->submit([d, options] {
        if (options.type < Local || options.type > Dynamic || options.listenHost.contains(QChar::Null) ||
            (options.type != Dynamic && (options.destinationHost.isEmpty() ||
                                         options.destinationHost.contains(QChar::Null) || !options.destinationPort))) {
            d->fail(InvalidConfiguration, QStringLiteral("Invalid tunnel options"));
        } else {
            d->start(options);
        }
    });
    Q_EMIT stateChanged(Opening);
}

void QSshTunnel::close() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshTunnel);
    if (d->frontClosing || d->frontState == Closed) {
        return;
    }
    d->frontClosing = true;
    d->submit([d] { d->shutdown(); });
}
