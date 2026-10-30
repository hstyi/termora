#include "qsshclient_p.h"
#include "qsshforwardstream_p.h"
#include "qsshsession_p.h"

#include <QFile>
#include <QUrl>
#include <algorithm>
#include <array>

namespace {
    constexpr int Capacity = 256 * 1024; ///< 每方向队列容量，限制慢速目标占用的内存。
    constexpr int Chunk = 32 * 1024;     ///< 每次 Session 调度的单流读写预算。

    /** 将 bytes 的两个大端字节解码为端口或长度。 */
    quint16 big16(const char *bytes) {
        return (quint16(quint8(bytes[0])) << 8) | quint8(bytes[1]);
    }
} // namespace

QSshForwardStream::QSshForwardStream(QSshSessionPrivate *owner, ssh_channel accepted)
    : session(owner),
      socket(owner->client->io),
      resolver(owner->client->io),
      deadline(owner->client->io),
      openEvent(owner->client->io),
      inputEvent(owner->client->io),
      outputEvent(owner->client->io),
      channel(accepted) {}

QSshForwardStream::~QSshForwardStream() {
    Q_ASSERT(!channel);
}

void QSshForwardStream::launch(asio::awaitable<void> operation) {
    asio::co_spawn(session->client->io, std::move(operation), [self = shared_from_this()](std::exception_ptr error) {
        if (error && !self->closed) {
            self->close(QStringLiteral("Forwarding connection failed"));
        }
    });
}

void QSshForwardStream::startDeadline() {
    deadline.expires_after(std::chrono::milliseconds(session->options.channelTimeoutMs));
    deadline.async_wait([self = shared_from_this()](const asio::error_code &error) {
        if (!error && !self->closed) {
            self->close(QStringLiteral("Forwarding connection timed out"));
        }
    });
}

void QSshForwardStream::startOutgoing(asio::ip::tcp::socket accepted, const QString &target, quint16 targetPort,
                                      bool socks) {
    socket = std::move(accepted);
    const auto source = socket.remote_endpoint();
    sourceHost = QString::fromStdString(source.address().to_string());
    sourcePort = source.port();
    host = target;
    port = targetPort;
    dynamic = socks;
    startDeadline();
    launch(doOutgoing());
}

void QSshForwardStream::startIncoming(Kind endpointKind, const QString &target, quint16 targetPort,
                                      const QString &socketPath) {
    host = target;
    port = targetPort;
    path = socketPath;
    kind = endpointKind;
    openSucceeded = true;
    startDeadline();
    launch(doIncoming());
}

asio::awaitable<void> QSshForwardStream::doWait(asio::steady_timer &event) {
    event.expires_at(asio::steady_timer::time_point::max());
    asio::error_code ignored;
    co_await event.async_wait(asio::redirect_error(asio::use_awaitable, ignored));
}

asio::awaitable<QByteArray> QSshForwardStream::doReadExactly(int count) {
    QByteArray data(count, '\0');
    co_await asio::async_read(socket, asio::buffer(data.data(), data.size()), asio::use_awaitable);
    co_return data;
}

asio::awaitable<void> QSshForwardStream::doWrite(QByteArray data) {
    co_await asio::async_write(socket, asio::buffer(data.constData(), data.size()), asio::use_awaitable);
}

asio::awaitable<bool> QSshForwardStream::doSocks() {
    auto bytes = co_await doReadExactly(1);
    if (closed) {
        co_return false;
    }
    socksVersion = quint8(bytes[0]);
    if (socksVersion == 5) {
        bytes = co_await doReadExactly(1);
        if (closed) {
            co_return false;
        }
        const int count = quint8(bytes[0]);
        bytes = co_await doReadExactly(count);
        if (closed) {
            co_return false;
        }
        if (!count || !bytes.contains('\0')) {
            co_await doWrite(QByteArray("\5\xff", 2));
            co_return false;
        }
        co_await doWrite(QByteArray("\5\0", 2));
        if (closed) {
            co_return false;
        }
        bytes = co_await doReadExactly(4);
        if (closed) {
            co_return false;
        }
        if (quint8(bytes[0]) != 5 || quint8(bytes[1]) != 1 || quint8(bytes[2]) != 0) {
            co_await doWrite(QByteArray("\5\7\0\1\0\0\0\0\0\0", 10));
            co_return false;
        }
        const int type = quint8(bytes[3]);
        if (type == 1 || type == 4) {
            bytes = co_await doReadExactly(type == 1 ? 4 : 16);
            if (closed) {
                co_return false;
            }
            if (type == 1) {
                asio::ip::address_v4::bytes_type value;
                std::copy_n(reinterpret_cast<const unsigned char *>(bytes.constData()), value.size(), value.begin());
                host = QString::fromStdString(asio::ip::address_v4(value).to_string());
            } else {
                asio::ip::address_v6::bytes_type value;
                std::copy_n(reinterpret_cast<const unsigned char *>(bytes.constData()), value.size(), value.begin());
                host = QString::fromStdString(asio::ip::address_v6(value).to_string());
            }
        } else if (type == 3) {
            bytes = co_await doReadExactly(1);
            if (closed) {
                co_return false;
            }
            bytes = co_await doReadExactly(quint8(bytes[0]));
            if (closed) {
                co_return false;
            }
            host = QString::fromLatin1(bytes);
        } else {
            co_await doWrite(QByteArray("\5\10\0\1\0\0\0\0\0\0", 10));
            co_return false;
        }
        bytes = co_await doReadExactly(2);
        if (closed) {
            co_return false;
        }
        port = big16(bytes.constData());
    } else if (socksVersion == 4) {
        bytes = co_await doReadExactly(7);
        if (closed) {
            co_return false;
        }
        if (quint8(bytes[0]) != 1) {
            co_return false;
        }
        port = big16(bytes.constData() + 1);
        const QByteArray address = bytes.mid(3, 4);
        // user ID 与 SOCKS4a hostname 均有长度上限，防止不终止的握手占用内存。
        QByteArray user;
        do {
            bytes = co_await doReadExactly(1);
            if (closed) {
                co_return false;
            }
            user += bytes;
        } while (bytes[0] && user.size() <= 1024);
        if (user.size() > 1024) {
            co_return false;
        }
        if (address.left(3) == QByteArray(3, '\0') && address[3] != 0) {
            QByteArray domain;
            do {
                bytes = co_await doReadExactly(1);
                if (closed) {
                    co_return false;
                }
                domain += bytes;
            } while (bytes[0] && domain.size() <= 255);
            if (domain.size() > 255) {
                co_return false;
            }
            domain.chop(1);
            host = QString::fromLatin1(domain);
        } else {
            host = QStringLiteral("%1.%2.%3.%4")
                       .arg(quint8(address[0]))
                       .arg(quint8(address[1]))
                       .arg(quint8(address[2]))
                       .arg(quint8(address[3]));
        }
    } else {
        co_return false;
    }
    co_return port && !host.isEmpty() && !host.contains(QChar::Null);
}

asio::awaitable<void> QSshForwardStream::doOutgoing() {
    if (closed) {
        co_return;
    }
    if (dynamic && !co_await doSocks()) {
        if (!closed) {
            close();
        }
        co_return;
    }
    if (closed) {
        co_return;
    }
    channel = ssh_channel_new(session->handle);
    if (!channel) {
        close(QStringLiteral("Unable to allocate forwarding channel"));
        co_return;
    }
    opening = true;
    session->schedule();
    while (!closed && opening) {
        co_await doWait(openEvent);
    }
    if (closed) {
        co_return;
    }
    if (dynamic) {
        QByteArray reply = socksVersion == 5 ? QByteArray("\5\0\0\1\0\0\0\0\0\0", 10) : QByteArray(8, '\0');
        reply[1] = socksVersion == 5 ? (openSucceeded ? 0 : 5) : (openSucceeded ? 90 : 91);
        co_await doWrite(reply);
        if (closed) {
            co_return;
        }
    }
    if (!openSucceeded) {
        close(QStringLiteral("SSH forwarding destination rejected"));
        co_return;
    }
    beginBridge();
}

asio::awaitable<bool> QSshForwardStream::doX11Authentication() {
    while (!closed && toSocket.size() < 12) {
        co_await doWait(outputEvent);
    }
    if (closed) {
        co_return false;
    }
    const bool little = toSocket[0] == 'l';
    if (!little && toSocket[0] != 'B') {
        co_return false;
    }
    const auto decode = [little](const char *value) {
        return little ? quint16(quint8(value[0]) | (quint16(quint8(value[1])) << 8)) : big16(value);
    };
    const int nameLength = decode(toSocket.constData() + 6);
    const int cookieLength = decode(toSocket.constData() + 8);
    const int cookieOffset = 12 + ((nameLength + 3) & ~3);
    const int total = cookieOffset + ((cookieLength + 3) & ~3);
    if (nameLength != 18 || cookieLength != 16 || total > Capacity) {
        co_return false;
    }
    while (!closed && toSocket.size() < total) {
        co_await doWait(outputEvent);
    }
    if (closed) {
        co_return false;
    }
    if (toSocket.mid(12, nameLength) != "MIT-MAGIC-COOKIE-1" ||
        toSocket.mid(cookieOffset, cookieLength) != session->x11FakeCookie) {
        co_return false;
    }
    // X11 setup 校验通过后才能连接本地显示，真实 cookie 不会发往 SSH 服务器。
    toSocket.replace(cookieOffset, cookieLength, session->options.x11.authenticationCookie);
    co_return true;
}

asio::awaitable<void> QSshForwardStream::doIncoming() {
    // 入站 libssh 回调返回后才继续，避免在 C 库尚未完成 Channel 初始化时重入。
    co_await asio::post(asio::use_awaitable);
    if (closed) {
        co_return;
    }
    if (kind == X11 && !co_await doX11Authentication()) {
        if (!closed) {
            close(QStringLiteral("X11 authentication cookie rejected"));
        }
        co_return;
    }
    if (closed) {
        co_return;
    }
    if (!path.isEmpty()) {
#ifdef ASIO_HAS_WINDOWS_STREAM_HANDLE
        if (path.startsWith(QStringLiteral("\\\\.\\pipe\\"), Qt::CaseInsensitive)) {
            HANDLE handle = INVALID_HANDLE_VALUE;
            while (!closed) {
                handle = CreateFileW(reinterpret_cast<const wchar_t *>(path.utf16()), GENERIC_READ | GENERIC_WRITE, 0,
                                     nullptr, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, nullptr);
                if (handle != INVALID_HANDLE_VALUE) {
                    break;
                }
                if (GetLastError() != ERROR_PIPE_BUSY) {
                    close(QStringLiteral("Unable to connect to SSH agent pipe"));
                    co_return;
                }
                // 管道忙时异步重试，整体 deadline 仍生效，不在唯一 worker 上调用 WaitNamedPipe。
                asio::steady_timer retry(socket.get_executor());
                retry.expires_after(std::chrono::milliseconds(10));
                co_await retry.async_wait(asio::use_awaitable);
            }
            if (closed) {
                co_return;
            }
            pipe = std::make_unique<asio::windows::stream_handle>(socket.get_executor(), handle);
        } else
#endif
        {
#ifdef ASIO_HAS_LOCAL_SOCKETS
            localSocket = std::make_unique<asio::local::stream_protocol::socket>(socket.get_executor());
            co_await localSocket->async_connect(
                asio::local::stream_protocol::endpoint(QFile::encodeName(path).toStdString()), asio::use_awaitable);
#else
            close(QStringLiteral("Local socket forwarding is unavailable"));
            co_return;
#endif
        }
    } else {
        const auto addresses = co_await resolver.async_resolve(host.toStdString(), std::to_string(port),
                                                               asio::use_awaitable);
        if (closed) {
            co_return;
        }
        co_await asio::async_connect(socket, addresses, asio::use_awaitable);
    }
    if (!closed) {
        beginBridge();
    }
}

void QSshForwardStream::beginBridge() {
    deadline.cancel();
    bridgeReady = true;
    launch(doReadSocket());
    session->schedule();
}

asio::awaitable<void> QSshForwardStream::doReadSocket() {
    std::array<char, Chunk> data;
    while (!closed) {
        while (!closed && toSsh.size() >= Capacity) {
            co_await doWait(inputEvent);
        }
        if (closed) {
            co_return;
        }
        const auto buffer = asio::buffer(data.data(), std::min<int>(data.size(), Capacity - toSsh.size()));
        asio::error_code error;
        size_t count = 0;
#ifdef ASIO_HAS_LOCAL_SOCKETS
        if (localSocket) {
            count = co_await localSocket->async_read_some(buffer, asio::redirect_error(asio::use_awaitable, error));
        } else
#endif
#ifdef ASIO_HAS_WINDOWS_STREAM_HANDLE
            if (pipe) {
            count = co_await pipe->async_read_some(buffer, asio::redirect_error(asio::use_awaitable, error));
        } else
#endif
        {
            count = co_await socket.async_read_some(buffer, asio::redirect_error(asio::use_awaitable, error));
        }
        if (closed) {
            co_return;
        }
        if (count) {
            toSsh.append(data.data(), count);
        }
        if (error == asio::error::eof || !count) {
            localEof = true;
            session->schedule();
            co_return;
        }
        if (error) {
            close(QStringLiteral("Forwarding socket read failed"));
            co_return;
        }
        session->schedule();
    }
}

asio::awaitable<void> QSshForwardStream::doWriteSocket() {
    while (!closed && !toSocket.isEmpty()) {
        const QByteArray data = toSocket.left(Chunk);
        const auto buffer = asio::buffer(data.constData(), data.size());
#ifdef ASIO_HAS_LOCAL_SOCKETS
        if (localSocket) {
            co_await asio::async_write(*localSocket, buffer, asio::use_awaitable);
        } else
#endif
#ifdef ASIO_HAS_WINDOWS_STREAM_HANDLE
            if (pipe) {
            co_await asio::async_write(*pipe, buffer, asio::use_awaitable);
        } else
#endif
        {
            co_await asio::async_write(socket, buffer, asio::use_awaitable);
        }
        if (closed) {
            co_return;
        }
        toSocket.remove(0, data.size());
        session->schedule();
    }
    writing = false;
    if (!closed) {
        session->schedule();
    }
}

bool QSshForwardStream::advance() {
    if (closed || !channel) {
        return false;
    }
    if (opening) {
        const auto destination = host.toUtf8();
        const auto source = sourceHost.toUtf8();
        const int result = ssh_channel_open_forward(channel, destination.constData(), port, source.constData(),
                                                    sourcePort);
        if (result == SSH_AGAIN) {
            return false;
        }
        opening = false;
        openSucceeded = result == SSH_OK;
        openEvent.cancel_one();
        return true;
    }
    if (!openSucceeded) {
        return false;
    }
    bool progress = false;
    if (toSocket.size() < Capacity) {
        std::array<char, Chunk> data;
        const int count = ssh_channel_read_nonblocking(channel, data.data(),
                                                       std::min<int>(Chunk, Capacity - toSocket.size()), 0);
        if (count == SSH_ERROR) {
            close(QStringLiteral("SSH forwarding read failed"));
            return true;
        }
        if (count > 0) {
            toSocket.append(data.data(), count);
            outputEvent.cancel_one();
            progress = true;
        }
    }
    remoteEof = ssh_channel_is_eof(channel) != 0;
    if (bridgeReady && !writing && !toSocket.isEmpty()) {
        writing = true;
        launch(doWriteSocket());
    }
    if (!toSsh.isEmpty()) {
        const int count = ssh_channel_write(channel, toSsh.constData(), std::min<int>(Chunk, toSsh.size()));
        if (count == SSH_ERROR) {
            close(QStringLiteral("SSH forwarding write failed"));
            return true;
        }
        if (count > 0) {
            toSsh.remove(0, count);
            inputEvent.cancel_one();
            progress = true;
        }
    }
    if (localEof && toSsh.isEmpty() && !eofSent) {
        const int result = ssh_channel_send_eof(channel);
        if (result == SSH_ERROR) {
            close(QStringLiteral("SSH forwarding EOF failed"));
            return true;
        }
        if (result == SSH_OK) {
            eofSent = true;
            progress = true;
        }
    }
    if (bridgeReady && remoteEof && toSocket.isEmpty() && !writing && !socketSendClosed) {
        asio::error_code ignored;
#ifdef ASIO_HAS_LOCAL_SOCKETS
        if (localSocket) {
            localSocket->shutdown(asio::local::stream_protocol::socket::shutdown_send, ignored);
        } else
#endif
        {
            if (socket.is_open()) {
                socket.shutdown(asio::ip::tcp::socket::shutdown_send, ignored);
            }
        }
        socketSendClosed = true;
    }
    if (toSocket.isEmpty() && !writing && (ssh_channel_is_closed(channel) || (remoteEof && eofSent))) {
        close();
        return true;
    }
    return progress;
}

void QSshForwardStream::close(const QString &report) {
    if (closed) {
        return;
    }
    closed = true;
    deadline.cancel();
    openEvent.cancel();
    inputEvent.cancel();
    outputEvent.cancel();
    resolver.cancel();
    asio::error_code ignored;
    socket.close(ignored);
#ifdef ASIO_HAS_LOCAL_SOCKETS
    if (localSocket) {
        localSocket->close(ignored);
    }
#endif
#ifdef ASIO_HAS_WINDOWS_STREAM_HANDLE
    if (pipe) {
        pipe->close(ignored);
    }
#endif
    if (channel) {
        ssh_channel_free(channel);
        channel = nullptr;
    }
    if (!report.isEmpty()) {
        session->reportForwardingError(report);
        if (onError) {
            onError(report);
        }
    }
    session->schedule();
}
