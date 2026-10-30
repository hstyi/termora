#include "qsshclient_p.h"
#include "qsshforwardstream_p.h"
#include "qsshsession_p.h"
#include "qsshtunnel_p.h"

#include <QDir>
#include <QFile>
#include <algorithm>

namespace {
    /** 按 Xauthority 的大端长度读取下一段字段，offset 始终指向未消费字节。 */
    bool readAuthorityField(const QByteArray &data, int &offset, QByteArray &field) {
        if (offset + 2 > data.size()) {
            return false;
        }
        const int length = (quint8(data[offset]) << 8) | quint8(data[offset + 1]);
        offset += 2;
        if (offset + length > data.size()) {
            return false;
        }
        field = data.mid(offset, length);
        offset += length;
        return true;
    }

    /** 读取 file 中 displayNumber 对应的 MIT cookie，不执行外部 xauth 程序。 */
    QByteArray readAuthority(const QString &file, int displayNumber) {
        QFile input(file);
        if (!input.open(QIODevice::ReadOnly) || input.size() > 1024 * 1024) {
            return {};
        }
        const auto data = input.readAll();
        int offset = 0;
        while (offset + 2 <= data.size()) {
            offset += 2; // family 后依次为 address、display number、authentication name、data。
            QByteArray address, number, name, cookie;
            if (!readAuthorityField(data, offset, address) || !readAuthorityField(data, offset, number) ||
                !readAuthorityField(data, offset, name) || !readAuthorityField(data, offset, cookie)) {
                return {};
            }
            if (number == QByteArray::number(displayNumber) && name == "MIT-MAGIC-COOKIE-1" && cookie.size() == 16) {
                return cookie;
            }
        }
        return {};
    }
} // namespace

bool QSshSessionPrivate::initializeForwarding() {
    if (options.agentForwarding && options.agentSocketPath.isEmpty()) {
#ifdef Q_OS_WIN
        options.agentSocketPath = QStringLiteral("\\\\.\\pipe\\openssh-ssh-agent");
#else
        options.agentSocketPath = qEnvironmentVariable("SSH_AUTH_SOCK");
#endif
        if (options.agentSocketPath.isEmpty()) {
            fail(QSshSession::InvalidConfiguration, QStringLiteral("Agent forwarding requires an agent socket"));
            return false;
        }
    }
    if (options.x11.enabled) {
        if (options.x11.display.isEmpty()) {
            options.x11.display = qEnvironmentVariable("DISPLAY");
        }
        const auto display = options.x11.display;
        const int separator = display.lastIndexOf(':');
        const auto numbers = display.mid(separator + 1).split('.');
        bool valid = false;
        const int displayNumber = numbers.value(0).toInt(&valid);
        if (separator < 0 || !valid || displayNumber < 0 || displayNumber > 59535) {
            fail(QSshSession::InvalidConfiguration, QStringLiteral("Invalid X11 DISPLAY"));
            return false;
        }
        if (numbers.size() > 1) {
            x11Screen = numbers[1].toInt(&valid);
            if (!valid || x11Screen < 0) {
                fail(QSshSession::InvalidConfiguration, QStringLiteral("Invalid X11 screen"));
                return false;
            }
        }
        x11Host = display.left(separator);
#if defined(ASIO_HAS_LOCAL_SOCKETS) && !defined(Q_OS_WIN)
        if (display.startsWith('/')) {
            x11SocketPath = numbers.size() > 1 ? display.left(display.lastIndexOf('.')) : display;
        } else if (x11Host.isEmpty() || x11Host == QStringLiteral("unix") ||
                   x11Host.endsWith(QStringLiteral("/unix"))) {
            x11SocketPath = QStringLiteral("/tmp/.X11-unix/X%1").arg(displayNumber);
        }
#endif
        if (x11Host.isEmpty() || x11Host == QStringLiteral("unix")) {
            x11Host = QStringLiteral("127.0.0.1");
        }
        x11Port = 6000 + displayNumber;
        if (options.x11.authenticationCookie.isEmpty()) {
            auto file = options.x11.authorityFile;
            if (file.isEmpty()) {
                file = qEnvironmentVariable("XAUTHORITY");
            }
            if (file.isEmpty()) {
                file = QDir::homePath() + QStringLiteral("/.Xauthority");
            }
            options.x11.authenticationCookie = readAuthority(file, displayNumber);
        }
        if (options.x11.authenticationCookie.size() != 16) {
            fail(QSshSession::InvalidConfiguration, QStringLiteral("X11 requires a 16-byte MIT-MAGIC-COOKIE-1 cookie"));
            return false;
        }
        x11FakeCookie.resize(16);
        if (!ssh_get_random(x11FakeCookie.data(), x11FakeCookie.size(), 1)) {
            fail(QSshSession::HandshakeError, QStringLiteral("Unable to generate X11 forwarding cookie"));
            return false;
        }
    }
    sessionCallbacks.userdata = this;
    sessionCallbacks.channel_open_request_x11_function = [](ssh_session handle, const char *, int,
                                                            void *userdata) -> ssh_channel {
        auto *d = static_cast<QSshSessionPrivate *>(userdata);
        if (!d->x11Requested || d->terminated || d->streams.size() >= MaximumForwardedConnections ||
            (d->options.x11.singleConnection && d->x11Accepted)) {
            return nullptr;
        }
        auto channel = ssh_channel_new(handle);
        if (!channel) {
            return nullptr;
        }
        d->x11Accepted = true;
        auto stream = std::make_shared<QSshForwardStream>(d, channel);
        d->streams.push_back(stream);
        stream->startIncoming(QSshForwardStream::X11, d->x11Host, d->x11Port, d->x11SocketPath);
        d->schedule();
        return channel;
    };
    sessionCallbacks.channel_open_request_auth_agent_function = [](ssh_session handle, void *userdata) -> ssh_channel {
        auto *d = static_cast<QSshSessionPrivate *>(userdata);
        if (!d->agentRequested || d->terminated || d->streams.size() >= MaximumForwardedConnections) {
            return nullptr;
        }
        auto channel = ssh_channel_new(handle);
        if (!channel) {
            return nullptr;
        }
        auto stream = std::make_shared<QSshForwardStream>(d, channel);
        d->streams.push_back(stream);
        stream->startIncoming(QSshForwardStream::Agent, {}, 0, d->options.agentSocketPath);
        d->schedule();
        return channel;
    };
    sessionCallbacks.channel_open_request_forwarded_tcpip_function =
        [](ssh_session handle, const char *address, int port, const char *, int, void *userdata) -> ssh_channel {
        auto *d = static_cast<QSshSessionPrivate *>(userdata);
        if (d->terminated || d->incomingForwards.size() + d->streams.size() >= MaximumForwardedConnections ||
            d->tunnels.empty()) {
            return nullptr;
        }
        auto channel = ssh_channel_new(handle);
        if (channel) {
            d->incomingForwards.push_back({channel, QString::fromUtf8(address), quint16(port)});
            d->schedule();
        }
        return channel;
    };
    ssh_callbacks_init(&sessionCallbacks);
    if (ssh_set_callbacks(handle, &sessionCallbacks) != SSH_OK) {
        fail(QSshSession::HandshakeError, sshError());
        return false;
    }
    return true;
}

void QSshSessionPrivate::requestRemote(bool cancel, const QString &address, quint16 port,
                                       std::function<void(int, int)> completed) {
    if (terminated) {
        return;
    }
    remoteRequests.push_back({cancel, address.toUtf8(), port, std::move(completed)});
    schedule();
}

bool QSshSessionPrivate::advanceForwarding() {
    bool progress = false;
    if (!remoteRequests.empty()) {
        auto &request = remoteRequests.front();
        int port = request.port;
        const int result = request.cancel
                               ? ssh_channel_cancel_forward(handle, request.address.constData(), request.port)
                               : ssh_channel_listen_forward(handle, request.address.constData(), request.port, &port);
        if (result != SSH_AGAIN) {
            const bool invalidPort = !request.cancel && result == SSH_OK && (port <= 0 || port > 65535);
            auto completed = std::move(request.completed);
            remoteRequests.pop_front();
            completed(invalidPort ? SSH_ERROR : result, port);
            progress = true;
        }
    }
    // 先更新全局请求的实际端口，再路由同一网络批次中到达的 forwarded-tcpip。
    auto incoming = std::move(incomingForwards);
    incomingForwards.clear();
    for (const auto &entry : incoming) {
        QSshTunnelPrivate *target = nullptr;
        for (auto *tunnel : tunnels) {
            if (tunnel->phase == QSshTunnel::Listening && tunnel->options.type == QSshTunnel::Remote &&
                tunnel->boundPort == entry.port) {
                const auto &address = tunnel->options.listenHost;
                if (address == entry.address) {
                    target = tunnel;
                    break;
                }
                if (address.isEmpty() || address == QStringLiteral("0.0.0.0") || address == QStringLiteral("::") ||
                    (address == QStringLiteral("localhost") &&
                     (entry.address == QStringLiteral("127.0.0.1") || entry.address == QStringLiteral("::1")))) {
                    target = tunnel;
                }
            }
        }
        if (target) {
            target->acceptRemote(entry.channel);
        } else {
            ssh_channel_free(entry.channel);
        }
        progress = true;
    }
    // 读取 libssh 可能再次触发入站回调；遍历 shared_ptr 快照，避免 vector 扩容使迭代器失效。
    const auto current = streams;
    for (const auto &stream : current) {
        progress = stream->advance() || progress;
    }
    std::erase_if(streams, [](const auto &stream) { return stream->closed; });
    return progress;
}

void QSshSessionPrivate::shutdownForwarding() {
    for (auto *tunnel : tunnels) {
        tunnel->shutdown(true);
    }
    for (const auto &stream : streams) {
        stream->close();
    }
    streams.clear();
    for (const auto &entry : incomingForwards) {
        ssh_channel_free(entry.channel);
    }
    incomingForwards.clear();
    remoteRequests.clear();
}

void QSshSessionPrivate::reportForwardingError(const QString &error) {
    notify([this, error] { Q_EMIT q->forwardingError(error); });
}
