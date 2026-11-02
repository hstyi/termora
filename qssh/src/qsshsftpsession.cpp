#include "qsshclient_p.h"
#include "qsshsession_p.h"
#include "qsshsftppermissions_p.h"
#include "qsshsftpsession_p.h"
#include "qsshsftptransfer_p.h"

#include <QPointer>
#include <QThread>
#include <QtEndian>
#include <algorithm>
#include <array>
#include <limits>
#include <stdexcept>
#include <utility>

namespace {
    using Sftp = QSshSftpSession;
    constexpr qsizetype MaximumPacket = 1024 * 1024;          ///< 限制恶意或异常服务器的单报文内存占用。
    constexpr qsizetype MaximumQueuedBytes = 4 * 1024 * 1024; ///< 未完成写入总预算。
    constexpr quint32 TransferChunk = 64 * 1024;              ///< 单次文件块及每次驱动收发预算。
    constexpr size_t MaximumHandles = 128;                    ///< 每个子系统同时打开的文件上限。

    /** SFTP v3 报文类型；加密、SSH 流控和 channel 多路复用仍由 libssh 负责。 */
    enum Packet : quint8 {
        Init = 1,
        Version = 2,
        Open = 3,
        Close = 4,
        Read = 5,
        Write = 6,
        Lstat = 7,
        Setstat = 9,
        Opendir = 11,
        Readdir = 12,
        Remove = 13,
        Mkdir = 14,
        Rmdir = 15,
        Realpath = 16,
        Stat = 17,
        Rename = 18,
        Readlink = 19,
        Symlink = 20,
        Status = 101,
        Handle = 102,
        Data = 103,
        Name = 104,
        Attrs = 105
    };

    /** 将 value 以协议要求的网络字节序追加到 buffer。 */
    void append32(QByteArray &buffer, const quint32 value) {
        const auto encoded = qToBigEndian(value);
        buffer.append(reinterpret_cast<const char *>(&encoded), sizeof(encoded));
    }

    /** 将 64 位文件偏移 value 追加到 buffer。 */
    void append64(QByteArray &buffer, const quint64 value) {
        append32(buffer, static_cast<quint32>(value >> 32));
        append32(buffer, static_cast<quint32>(value));
    }

    /** 将不透明字节串 value 按 uint32 长度前缀追加到 buffer。 */
    void appendString(QByteArray &buffer, const QByteArray &value) {
        append32(buffer, static_cast<quint32>(value.size()));
        buffer.append(value);
    }

    /** 有界报文读取器；所有格式错误抛异常，由协程完成处理器统一关闭当前 SFTP 子系统。 */
    class Reader {
    public:
        /** 在 data 中跳过 skip 个已解析字节，保留独立共享副本。 */
        explicit Reader(QByteArray data, const qsizetype skip = 0) : data(std::move(data)), offset(skip) {}

        /** 返回下一个大端 uint32，长度不足时拒绝报文。 */
        quint32 number() {
            require(4);
            const auto value = qFromBigEndian<quint32>(data.constData() + offset);
            offset += 4;
            return value;
        }

        /** 返回下一个大端 uint64。 */
        quint64 number64() {
            const quint64 high = number();
            return (high << 32) | number();
        }

        /** 返回下一个长度前缀字节串，不解释远端文件句柄的内容。 */
        QByteArray string() {
            const quint32 size = number();
            require(size);
            auto value = data.mid(offset, size);
            offset += size;
            return value;
        }

        /** 读取 SFTP v3 属性，忽略有界扩展属性，不假设服务器一定返回全部字段。 */
        Sftp::FileInfo attributes() {
            Sftp::FileInfo info;
            const auto flags = number();
            if (flags & ~quint32(0x8000000f)) {
                throw std::runtime_error("Unknown SFTP v3 attribute flags");
            }
            info.attributes = Sftp::Attributes(static_cast<Sftp::Attribute>(flags & 0x0f));
            if (flags & Sftp::Size) {
                info.size = number64();
            }
            if (flags & Sftp::UserAndGroup) {
                info.uid = number();
                info.gid = number();
            }
            if (flags & Sftp::Permissions) {
                info.mode = number();
                info.permissions = qssh_sftp::permissionsFromMode(info.mode);
            }
            if (flags & Sftp::Times) {
                info.accessTime = number();
                info.modificationTime = number();
            }
            if (flags & 0x80000000) {
                const auto count = number();
                if (count > static_cast<quint32>(remaining() / 8)) {
                    throw std::runtime_error("Invalid SFTP extended attribute count");
                }
                for (quint32 i = 0; i < count; ++i) {
                    string();
                    string();
                }
            }
            return info;
        }

        /** 返回剩余字节数。 */
        qsizetype remaining() const {
            return data.size() - offset;
        }

        /** 要求响应无多余字节，防止错误地接受不同类型报文。 */
        void end() const {
            if (remaining()) {
                throw std::runtime_error("Trailing bytes in SFTP response");
            }
        }

    private:
        /** 检查接下来 count 字节均在当前有界报文内。 */
        void require(qsizetype count) const {
            if (count < 0 || count > remaining()) {
                throw std::runtime_error("Truncated SFTP response");
            }
        }

        QByteArray data;      ///< 当前响应的共享字节副本。
        qsizetype offset = 0; ///< 已解析位置。
    };

    /** 从 packet 解析状态 code 和远端 message，拒绝非法状态码或格式。 */
    void readStatus(const QByteArray &packet, Sftp::Error &code, QString &message) {
        if (packet.isEmpty() || static_cast<quint8>(packet[0]) != Status) {
            throw std::runtime_error("Expected SFTP STATUS response");
        }
        Reader reader(packet, 5);
        const auto value = reader.number();
        if (value > Sftp::Unsupported) {
            throw std::runtime_error("Invalid SFTP v3 status code");
        }
        code = static_cast<Sftp::Error>(value);
        message = QString::fromUtf8(reader.string());
        reader.string(); // 语言标签不影响结果，仍需完整校验格式。
        reader.end();
    }

    /** 读取 NAME 列表，目录项数量受实际剩余报文长度约束。 */
    QList<Sftp::FileInfo> readNames(const QByteArray &packet) {
        Reader reader(packet, 5);
        const auto count = reader.number();
        if (count > static_cast<quint32>(reader.remaining() / 12)) {
            throw std::runtime_error("Invalid SFTP directory entry count");
        }
        QList<Sftp::FileInfo> entries;
        entries.reserve(count);
        for (quint32 i = 0; i < count; ++i) {
            const auto name = QString::fromUtf8(reader.string());
            const auto longName = QString::fromUtf8(reader.string());
            auto info = reader.attributes();
            info.name = name;
            info.longName = longName;
            entries.append(info);
        }
        reader.end();
        return entries;
    }

    /** 验证远端 path 的长度及 C 字符串兼容性，不将远端路径按本机语义规范化。 */
    bool validPath(const QString &path) {
        return !path.isEmpty() && !path.contains(QChar::Null) && path.toUtf8().size() <= 32 * 1024;
    }

    /** 检查 token 后才首次进入 operation，防止尚未启动的协程访问已释放 Private。 */
    asio::awaitable<void> runIfAlive(const std::shared_ptr<bool> token, asio::awaitable<void> operation) {
        if (*token) {
            co_await std::move(operation);
        }
    }
} // namespace

QSshSftpSessionPrivate::QSshSftpSessionPrivate(QSshSftpSession *owner, QSshSession *session)
    : q(owner),
      session(session->d_func()),
      progressEvent(this->session->client->io),
      deadline(this->session->client->io) {
    this->session->client->synchronize([this] { this->session->sftpSessions.push_back(this); });
}

QSshSftpSessionPrivate::~QSshSftpSessionPrivate() {
    session->client->synchronize([this] {
        shutdown();
        *alive = false;
        std::erase(session->sftpSessions, this);
        session->schedule();
    });
}

void QSshSftpSessionPrivate::submit(std::function<void()> task) const {
    asio::post(session->client->io, [token = alive, task = std::move(task)] {
        if (*token) {
            task();
        }
    });
}

void QSshSftpSessionPrivate::notify(std::function<void()> task) const {
    QMetaObject::invokeMethod(q, std::move(task), Qt::QueuedConnection);
}

quint64 QSshSftpSessionPrivate::enqueue(Request request) {
    Q_ASSERT(q->thread() == QThread::currentThread());
    if (frontState != Sftp::Ready || frontClosing || pendingBytes + request.data.size() > MaximumQueuedBytes ||
        nextRequestId == (quint64(1) << 63) - 1) {
        return 0;
    }
    request.id = ++nextRequestId;
    pendingBytes += request.data.size();
    submit([this, request] {
        if (terminated) {
            finishRequest(request, false, Sftp::SessionClosed, QStringLiteral("SFTP session closed"));
            return;
        }
        requests.push_back(request);
        pump();
    });
    return request.id;
}

void QSshSftpSessionPrivate::setState(Sftp::State state) {
    phase = state;
    notify([this, state] {
        if (frontState == state || (frontClosing && state != Sftp::Closed)) {
            return;
        }
        frontState = state;
        const QPointer guard(q);
        Q_EMIT q->stateChanged(state);
        if (!guard) {
            return;
        }
        if (state == Sftp::Ready && !frontClosing) {
            Q_EMIT q->ready();
        } else if (state == Sftp::Closed) {
            Q_EMIT q->closed();
        }
    });
}

void QSshSftpSessionPrivate::setDeadline() {
    deadline.expires_after(std::chrono::milliseconds(options.requestTimeoutMs));
    deadline.async_wait([this, token = alive](const asio::error_code &error) {
        if (*token && !error) {
            shutdown(Sftp::TimeoutError, QStringLiteral("SFTP operation timed out"));
        }
    });
}

void QSshSftpSessionPrivate::start(const Sftp::Options configuration) {
    if (terminated) {
        return;
    }
    options = configuration;
    if (session->terminated || session->phase != QSshSession::Ready || options.requestTimeoutMs <= 0 ||
        options.maxInFlightBytes < TransferChunk) {
        shutdown(Sftp::InvalidState, QStringLiteral("SFTP requires a ready SSH session and a positive timeout"));
        return;
    }
    phase = Sftp::Opening;
    channel = ssh_channel_new(session->handle);
    if (!channel) {
        shutdown(Sftp::Failure, session->sshError());
        return;
    }
    setDeadline();
    asio::co_spawn(session->client->io, runIfAlive(alive, doStart()),
                   [this, token = alive](const std::exception_ptr &error) {
                       if (!*token || terminated || !error) {
                           return;
                       }
                       try {
                           std::rethrow_exception(error);
                       } catch (const std::exception &exception) {
                           shutdown(Sftp::ProtocolError, QString::fromUtf8(exception.what()));
                       } catch (...) {
                           shutdown(Sftp::ProtocolError, QStringLiteral("Unexpected SFTP coroutine failure"));
                       }
                   });
}

asio::awaitable<bool> QSshSftpSessionPrivate::doWaitForProgress() {
    const auto token = alive;
    progressEvent.expires_at(asio::steady_timer::time_point::max());
    asio::error_code ignored;
    co_await progressEvent.async_wait(asio::redirect_error(asio::use_awaitable, ignored));
    co_return *token && !terminated;
}

asio::awaitable<void> QSshSftpSessionPrivate::doStart() {
    const auto token = alive;
    // libssh 的 channel 请求可安全重试；SFTP 元数据接口会同步等响应，因此此处自行分帧。
    for (int step = 0; step < 2; ++step) {
        while (*token && !terminated) {
            const int result = step == 0 ? ssh_channel_open_session(channel) : ssh_channel_request_sftp(channel);
            if (result == SSH_OK) {
                break;
            }
            if (result != SSH_AGAIN) {
                shutdown(Sftp::Failure, session->sshError());
                co_return;
            }
            session->armWaits();
            if (!co_await doWaitForProgress()) {
                co_return;
            }
        }
        if (!*token || terminated) {
            co_return;
        }
    }
    QByteArray version;
    subsystemReady = true;
    append32(version, 3);
    sendPacket(Init, version);
    while (!response) {
        if (!co_await doWaitForProgress()) {
            co_return;
        }
    }
    Reader reader(std::move(*response), 1);
    response.reset();
    if (reader.number() != 3) {
        throw std::runtime_error("Only SFTP version 3 is supported");
    }
    while (reader.remaining()) {
        reader.string(); // 服务器扩展名及版本；仅使用标准 v3 操作。
        reader.string();
    }
    negotiating = false;
    deadline.cancel();
    setState(Sftp::Ready);
    pump();
}

/** 返回 request 消耗的流水线字节数；元数据由顺序屏障限制，无需内容预算。 */
static qint64 requestCost(const QSshSftpSessionPrivate::Request &request) {
    return request.kind == QSshSftpSessionPrivate::Read ? request.value : request.data.size();
}

void QSshSftpSessionPrivate::enqueueInternal(Request request, std::function<void(Result)> completed) {
    request.id = ++nextInternalId;
    request.completed = std::move(completed);
    if (terminated) {
        finishRequest(request, false, Sftp::SessionClosed, QStringLiteral("SFTP session closed"));
        return;
    }
    requests.push_back(std::move(request));
    asio::post(session->client->io, [this, token = alive] {
        if (*token) {
            pump();
        }
    });
}

void QSshSftpSessionPrivate::pump() {
    if (terminated || phase != Sftp::Ready) {
        return;
    }
    while (!requests.empty()) {
        const auto &next = requests.front();
        if (next.cancelled && next.cancelled()) {
            auto request = std::move(requests.front());
            requests.pop_front();
            finishRequest(request, false, Sftp::Cancelled, QStringLiteral("Transfer cancelled before dispatch"));
            continue;
        }
        const bool data = next.kind == Read || next.kind == Write;
        if ((!data && !running.empty()) || inFlightBytes + requestCost(next) > options.maxInFlightBytes) {
            return;
        }
        for (const auto &[id, previous] : running) {
            if (previous.kind != Read && previous.kind != Write) {
                return;
            }
            if (previous.handle != next.handle || (previous.kind == Read && next.kind == Read)) {
                continue;
            }
            const auto oldSize = requestCost(previous), newSize = requestCost(next);
            const bool append = handleFlags.contains(next.handle) && (handleFlags.at(next.handle) & Sftp::Append);
            if (append || (next.offset < previous.offset + oldSize && previous.offset < next.offset + newSize)) {
                return; // 冲突区间及 Append 保持提交顺序，不能凭响应先后推断远端执行顺序。
            }
        }
        auto request = std::move(requests.front());
        requests.pop_front();
        request.reserved = true;
        inFlightBytes += requestCost(request);
        running.emplace(request.id, request);
        auto timer = std::make_shared<asio::steady_timer>(session->client->io);
        requestTimers.emplace(request.id, timer);
        timer->expires_after(std::chrono::milliseconds(options.requestTimeoutMs));
        timer->async_wait([this, token = alive](const asio::error_code &error) {
            if (*token && !error && !terminated) {
                shutdown(Sftp::TimeoutError, QStringLiteral("SFTP request timed out"));
            }
        });
        asio::co_spawn(session->client->io, runIfAlive(alive, doTask(request)),
                       [this, token = alive, request](std::exception_ptr error) {
                           if (!*token || terminated) {
                               return;
                           }
                           requestTimers.at(request.id)->cancel();
                           requestTimers.erase(request.id);
                           running.erase(request.id);
                           if (request.completed) {
                               inFlightBytes -= requestCost(request);
                           }
                           if (error) {
                               finishRequest(request, false, Sftp::ProtocolError,
                                             QStringLiteral("Invalid SFTP response"));
                               try {
                                   std::rethrow_exception(error);
                               } catch (const std::exception &e) {
                                   shutdown(Sftp::ProtocolError, QString::fromUtf8(e.what()));
                               } catch (...) {
                                   shutdown(Sftp::ProtocolError, QStringLiteral("SFTP request failed"));
                               }
                           } else {
                               pump();
                           }
                       });
        if (!data) {
            return;
        }
    }
}

asio::awaitable<void> QSshSftpSessionPrivate::doTask(Request request) {
    const auto token = alive;
    if (request.cancelled && request.cancelled()) {
        finishRequest(request, false, Sftp::Cancelled, QStringLiteral("Transfer cancelled before dispatch"));
        co_return;
    }
    const bool success = co_await doRequest(request);
    if (*token && !terminated && success) {
        finishRequest(request, true, Sftp::NoError, {});
    }
}

void QSshSftpSessionPrivate::sendPacket(const quint8 type, const QByteArray &payload) {
    if (payload.size() + 1 > MaximumPacket) {
        throw std::runtime_error("Invalid SFTP outbound packet state");
    }
    append32(output, static_cast<quint32>(payload.size() + 1));
    output.append(static_cast<char>(type));
    output.append(payload);
    session->schedule();
}

asio::awaitable<QByteArray> QSshSftpSessionPrivate::doExchange(const quint8 type, const QByteArray payload) {
    const auto token = alive;
    do {
        ++wireId;
    } while (exchanges.contains(wireId));
    const auto id = wireId;
    auto exchange = std::make_shared<Exchange>(session->client->io);
    exchanges.emplace(id, exchange);
    QByteArray body;
    append32(body, id);
    body.append(payload);
    sendPacket(type, body);
    while (!exchange->response) {
        exchange->event.expires_at(asio::steady_timer::time_point::max());
        asio::error_code ignored;
        co_await exchange->event.async_wait(asio::redirect_error(asio::use_awaitable, ignored));
        if (!*token || terminated) {
            co_return QByteArray();
        }
    }
    auto packet = std::move(*exchange->response);
    exchanges.erase(id);
    co_return packet;
}

bool QSshSftpSessionPrivate::advance() {
    if (terminated || !channel) {
        return false;
    }
    if (!subsystemReady) {
        progressEvent.cancel_one();
        return false;
    }
    bool progress = false;
    if (!output.isEmpty()) {
        const auto size = static_cast<quint32>(std::min<qsizetype>(output.size(), TransferChunk));
        const int result = ssh_channel_write(channel, output.constData(), size);
        if (result == SSH_ERROR) {
            shutdown(Sftp::ConnectionLost, session->sshError());
            return false;
        }
        if (result > 0) {
            output.remove(0, result);
            progress = true;
        }
    }
    std::array<char, TransferChunk> buffer{};
    const int received = ssh_channel_read_nonblocking(channel, buffer.data(), buffer.size(), 0);
    if (received == SSH_ERROR) {
        shutdown(Sftp::ConnectionLost, session->sshError());
        return false;
    }
    if (received > 0) {
        input.append(buffer.data(), received);
        progress = true;
    }
    while (input.size() >= 4) {
        const auto length = qFromBigEndian<quint32>(input.constData());
        if (length < (negotiating ? 5u : 9u) || length > MaximumPacket) {
            shutdown(Sftp::ProtocolError, QStringLiteral("Invalid SFTP response length"));
            return false;
        }
        if (input.size() < length + 4) {
            break;
        }
        auto packet = input.mid(4, length);
        input.remove(0, length + 4);
        if (negotiating) {
            if (response || static_cast<quint8>(packet[0]) != Version) {
                shutdown(Sftp::ProtocolError, QStringLiteral("Unexpected SFTP version response"));
                return false;
            }
            response = std::move(packet);
        } else {
            const auto id = qFromBigEndian<quint32>(packet.constData() + 1);
            const auto entry = exchanges.find(id);
            if (entry == exchanges.end() || entry->second->response) {
                shutdown(Sftp::ProtocolError, QStringLiteral("Unexpected SFTP response request ID"));
                return false;
            }
            entry->second->response = std::move(packet);
            entry->second->event.cancel_one();
        }
    }
    // 正常 EOF 也可能带着最后一个完整响应；先交给协程消费，再在下一次驱动检测关闭。
    if (received <= 0 && !response && (ssh_channel_is_eof(channel) || ssh_channel_is_closed(channel))) {
        shutdown(Sftp::ConnectionLost, QStringLiteral("SFTP channel closed by peer"));
        return false;
    }
    progressEvent.cancel_one(); // 同时唤醒 channel-open / subsystem-request 等 SSH 协商步骤。
    return progress;
}

void QSshSftpSessionPrivate::finishRequest(const Request &request, bool success, Sftp::Error error,
                                           const QString &message) {
    if (request.result->finished) {
        return;
    }
    request.result->finished = true;
    request.result->error = success ? Sftp::NoError : error;
    request.result->message = message;
    if (request.completed) {
        request.completed(*request.result);
        return;
    }
    notify([this, id = request.id, size = request.data.size(), cost = request.reserved ? requestCost(request) : 0,
            success, error, message] {
        pendingBytes -= size;
        // Qt 确认消费结果后才返还读缓冲预算，GUI 暂停时不会无限堆积 DATA 通知。
        submit([this, cost] {
            inFlightBytes -= cost;
            pump();
        });
        if (success) {
            Q_EMIT q->requestFinished(id);
        } else {
            Q_EMIT q->requestFailed(id, error, message);
        }
    });
}

void QSshSftpSessionPrivate::shutdown(Sftp::Error error, const QString &message) {
    if (terminated) {
        return;
    }
    terminated = true;
    for (auto *transfer : transfers) {
        const auto category = error == Sftp::TimeoutError ? QSshSftpTransfer::TimeoutError
                              : error == Sftp::SessionClosed || error == Sftp::Cancelled
                                  ? QSshSftpTransfer::SessionClosed
                                  : QSshSftpTransfer::RemoteError;
        transfer->finish(QSshSftpTransfer::Failed, category, message);
    }
    deadline.cancel();
    progressEvent.cancel();
    for (auto &[id, exchange] : exchanges) {
        exchange->event.cancel();
    }
    exchanges.clear();
    for (auto &[id, timer] : requestTimers) {
        timer->cancel();
    }
    requestTimers.clear();
    for (const auto &[id, request] : running) {
        finishRequest(request, false, error, message);
    }
    running.clear();
    for (const auto &request : requests) {
        finishRequest(request, false, error, message);
    }
    requests.clear();
    handles.clear();
    handleFlags.clear();
    input.clear();
    output.clear();
    response.reset();
    if (channel) {
        // 关闭整个子系统即可让服务器释放全部文件／目录句柄，不逐个发出可能等待响应的 CLOSE。
        ssh_channel_send_eof(channel);
        ssh_channel_close(channel);
        ssh_channel_free(channel);
        channel = nullptr;
    }
    if (error != Sftp::Cancelled) {
        notify([this, error, message] {
            frontError = error;
            frontErrorText = message;
            Q_EMIT q->errorOccurred(error);
        });
    }
    setState(Sftp::Closed);
    session->schedule();
}

asio::awaitable<bool> QSshSftpSessionPrivate::doRequest(const Request request) {
    const auto token = alive;
    const quint64 id = request.id;
    QByteArray payload;
    quint8 type = 0;
    quint8 expected = Status;
    switch (request.kind) {
        case Open:
            if (handles.size() >= MaximumHandles) {
                finishRequest(request, false, Sftp::Failure, QStringLiteral("Too many open SFTP files"));
                co_return false;
            }
            type = Packet::Open;
            expected = Handle;
            appendString(payload, request.path.toUtf8());
            append32(payload, request.value);
            append32(payload, Sftp::Permissions);
            append32(payload, request.mode);
            break;
        case Close:
        case Read:
        case Write: {
            const auto handle = handles.find(request.handle);
            if (handle == handles.end()) {
                finishRequest(request, false, Sftp::InvalidState, QStringLiteral("Invalid or closed SFTP file handle"));
                co_return false;
            }
            type = request.kind == Close ? Packet::Close : request.kind == Read ? Packet::Read : Packet::Write;
            appendString(payload, handle->second);
            if (request.kind != Close) {
                append64(payload, request.offset);
                if (request.kind == Read) {
                    append32(payload, request.value);
                    expected = Data;
                } else {
                    appendString(payload, request.data);
                }
            }
            break;
        }
        default:
            appendString(payload, request.path.toUtf8());
            switch (request.kind) {
                case List:
                    type = Opendir;
                    expected = Handle;
                    break;
                case Stat:
                    type = Packet::Stat;
                    expected = Attrs;
                    break;
                case Lstat:
                    type = Packet::Lstat;
                    expected = Attrs;
                    break;
                case Realpath:
                    type = Packet::Realpath;
                    expected = Name;
                    break;
                case Readlink:
                    type = Packet::Readlink;
                    expected = Name;
                    break;
                case Remove:
                    type = Packet::Remove;
                    break;
                case Rmdir:
                    type = Packet::Rmdir;
                    break;
                case Rename:
                    type = Packet::Rename;
                    appendString(payload, request.secondPath.toUtf8());
                    break;
                case Symlink:
                    type = Packet::Symlink;
                    appendString(payload, request.secondPath.toUtf8());
                    break;
                case Mkdir:
                case Chmod:
                    type = request.kind == Mkdir ? Packet::Mkdir : Setstat;
                    append32(payload, Sftp::Permissions);
                    append32(payload, request.value);
                    break;
                default:
                    throw std::runtime_error("Unknown SFTP operation");
            }
            break;
    }
    auto packet = co_await doExchange(type, payload);
    if (!*token || terminated) {
        co_return false;
    }
    const auto responseType = static_cast<quint8>(packet[0]);
    if (responseType == Status) {
        Sftp::Error code;
        QString message;
        readStatus(packet, code, message);
        if (code == Sftp::EndOfFile && request.kind == Read) {
            if (!request.completed) {
                notify([this, id] { Q_EMIT q->dataRead(id, {}); });
            }
            co_return true;
        }
        if (code != Sftp::NoError) {
            finishRequest(request, false, code, message);
            co_return false;
        }
        if (expected != Status) {
            throw std::runtime_error("Unexpected successful SFTP STATUS response");
        }
        if (request.kind == Close) {
            handles.erase(request.handle);
            handleFlags.erase(request.handle);
        }
        co_return true;
    }
    if (responseType != expected) {
        throw std::runtime_error("Unexpected SFTP response type");
    }
    Reader reader(packet, 5);
    if (expected == Data) {
        const auto data = reader.string();
        reader.end();
        if (data.size() > request.value) {
            throw std::runtime_error("SFTP read exceeds requested length");
        }
        request.result->data = data;
        if (!request.completed) {
            notify([this, id, data] { Q_EMIT q->dataRead(id, data); });
        }
    } else if (expected == Attrs) {
        auto info = reader.attributes();
        reader.end();
        info.name = request.path;
        request.result->info = info;
        if (!request.completed) {
            notify([this, id, info] { Q_EMIT q->fileInfoAvailable(id, info); });
        }
    } else if (expected == Name) {
        const auto names = readNames(packet);
        if (names.size() != 1) {
            throw std::runtime_error("Expected one SFTP resolved path");
        }
        notify([this, id, path = names.first().name] { Q_EMIT q->pathResolved(id, path); });
    } else if (expected == Handle) {
        const auto handle = reader.string();
        reader.end();
        if (handle.isEmpty() || handle.size() > 256) {
            throw std::runtime_error("Invalid SFTP handle length");
        }
        if (request.kind == Open) {
            handles.emplace(id, handle);
            handleFlags.emplace(id, request.value);
            request.result->handle = id;
            if (!request.completed) {
                notify([this, id] { Q_EMIT q->fileOpened(id, id); });
            }
        } else {
            QByteArray directory;
            appendString(directory, handle);
            Sftp::Error result = Sftp::NoError;
            QString message;
            while (*token && !terminated) {
                packet = co_await doExchange(Readdir, directory);
                if (!*token || terminated) {
                    co_return false;
                }
                if (static_cast<quint8>(packet[0]) == Status) {
                    readStatus(packet, result, message);
                    if (result == Sftp::NoError) {
                        throw std::runtime_error("Expected directory entries or EOF");
                    }
                    break;
                }
                if (static_cast<quint8>(packet[0]) != Name) {
                    throw std::runtime_error("Expected SFTP directory NAME response");
                }
                const auto entries = readNames(packet);
                if (entries.isEmpty()) {
                    throw std::runtime_error("Empty SFTP directory response without EOF");
                }
                batchPending = true;
                notify([this, id, entries] {
                    const QPointer guard(q);
                    Q_EMIT q->directoryEntries(id, entries);
                    if (guard) {
                        // Qt 消费一批后才允许下一批，防止大目录撑满跨线程通知队列。
                        submit([this] {
                            batchPending = false;
                            progressEvent.cancel_one();
                        });
                    }
                });
                while (batchPending) {
                    if (!co_await doWaitForProgress()) {
                        co_return false;
                    }
                }
            }
            // 即使 READDIR 失败，也先关闭临时目录句柄，避免正常请求错误导致远端资源泄漏。
            packet = co_await doExchange(Packet::Close, directory);
            if (!*token || terminated) {
                co_return false;
            }
            Sftp::Error closeResult;
            QString closeMessage;
            readStatus(packet, closeResult, closeMessage);
            if (result != Sftp::EndOfFile || closeResult != Sftp::NoError) {
                finishRequest(request, false, result == Sftp::EndOfFile ? closeResult : result,
                              result == Sftp::EndOfFile ? closeMessage : message);
                co_return false;
            }
        }
    }
    co_return true;
}

QSshSftpSession::QSshSftpSession(QSshSession *session)
    : QObject(session),
      d_ptr(new QSshSftpSessionPrivate(this, session)) {
    qRegisterMetaType<State>();
    qRegisterMetaType<Error>();
    qRegisterMetaType<FileInfo>();
    qRegisterMetaType<QList<FileInfo>>();
}

QSshSftpSession::~QSshSftpSession() {
    Q_ASSERT(thread() == QThread::currentThread());
    while (auto *transfer = findChild<QSshSftpTransfer *>(QString(), Qt::FindDirectChildrenOnly)) {
        delete transfer;
    }
}

QSshSftpSession::State QSshSftpSession::state() const {
    Q_D(const QSshSftpSession);
    return d->frontState;
}

QSshSftpSession::Error QSshSftpSession::error() const {
    Q_D(const QSshSftpSession);
    return d->frontError;
}

QString QSshSftpSession::errorString() const {
    Q_D(const QSshSftpSession);
    return d->frontErrorText;
}

void QSshSftpSession::open() {
    open(Options{});
}

void QSshSftpSession::open(const Options &options) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSftpSession);
    if (d->frontState != Idle || d->frontClosing) {
        return;
    }
    d->frontState = Opening;
    d->submit([d, options] { d->start(options); });
    Q_EMIT stateChanged(Opening);
}

void QSshSftpSession::close() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSftpSession);
    if (d->frontClosing || d->frontState == Closed) {
        return;
    }
    d->frontClosing = true;
    d->submit([d] { d->shutdown(Cancelled, QStringLiteral("SFTP session closed by application")); });
}

quint64 QSshSftpSession::listDirectory(const QString &path) {
    Q_D(QSshSftpSession);
    return validPath(path) ? d->enqueue({.kind = QSshSftpSessionPrivate::List, .path = path}) : 0;
}

quint64 QSshSftpSession::stat(const QString &path, const bool followSymbolicLinks) {
    Q_D(QSshSftpSession);
    return validPath(path)
               ? d->enqueue({.kind = followSymbolicLinks ? QSshSftpSessionPrivate::Stat : QSshSftpSessionPrivate::Lstat,
                             .path = path})
               : 0;
}

quint64 QSshSftpSession::canonicalPath(const QString &path) {
    Q_D(QSshSftpSession);
    return validPath(path) ? d->enqueue({.kind = QSshSftpSessionPrivate::Realpath, .path = path}) : 0;
}

quint64 QSshSftpSession::readLink(const QString &path) {
    Q_D(QSshSftpSession);
    return validPath(path) ? d->enqueue({.kind = QSshSftpSessionPrivate::Readlink, .path = path}) : 0;
}

quint64 QSshSftpSession::openFile(const QString &path, const OpenFlags flags, const quint32 permissions) {
    Q_D(QSshSftpSession);
    const auto bits = static_cast<quint32>(static_cast<int>(flags));
    if (!validPath(path) || !(bits & (Read | Write)) || bits & ~static_cast<quint32>(0x3f) ||
        (bits & (Append | Create | Truncate | Exclusive) && !(bits & Write)) ||
        (bits & Exclusive && !(bits & Create)) || permissions & ~static_cast<quint32>(07777)) {
        return 0;
    }
    return d->enqueue({.kind = QSshSftpSessionPrivate::Open, .path = path, .value = bits, .mode = permissions});
}

quint64 QSshSftpSession::closeFile(const quint64 handle) {
    Q_D(QSshSftpSession);
    return handle ? d->enqueue({.kind = QSshSftpSessionPrivate::Close, .handle = handle}) : 0;
}

quint64 QSshSftpSession::readFile(const quint64 handle, const quint64 offset, const quint32 maximumBytes) {
    Q_D(QSshSftpSession);
    if (!handle || !maximumBytes || maximumBytes > TransferChunk ||
        offset > std::numeric_limits<quint64>::max() - maximumBytes) {
        return 0;
    }
    return d->enqueue(
        {.kind = QSshSftpSessionPrivate::Read, .handle = handle, .offset = offset, .value = maximumBytes});
}

quint64 QSshSftpSession::writeFile(const quint64 handle, const quint64 offset, const QByteArray &data) {
    Q_D(QSshSftpSession);
    if (!handle || data.isEmpty() || data.size() > TransferChunk ||
        offset > std::numeric_limits<quint64>::max() - data.size()) {
        return 0;
    }
    return d->enqueue({.kind = QSshSftpSessionPrivate::Write, .handle = handle, .offset = offset, .data = data});
}

quint64 QSshSftpSession::makeDirectory(const QString &path, const quint32 permissions) {
    Q_D(QSshSftpSession);
    return validPath(path) && !(permissions & ~static_cast<quint32>(07777))
               ? d->enqueue({.kind = QSshSftpSessionPrivate::Mkdir, .path = path, .value = permissions})
               : 0;
}

quint64 QSshSftpSession::removeDirectory(const QString &path) {
    Q_D(QSshSftpSession);
    return validPath(path) ? d->enqueue({.kind = QSshSftpSessionPrivate::Rmdir, .path = path}) : 0;
}

quint64 QSshSftpSession::removeFile(const QString &path) {
    Q_D(QSshSftpSession);
    return validPath(path) ? d->enqueue({.kind = QSshSftpSessionPrivate::Remove, .path = path}) : 0;
}

quint64 QSshSftpSession::rename(const QString &oldPath, const QString &newPath) {
    Q_D(QSshSftpSession);
    return validPath(oldPath) && validPath(newPath)
               ? d->enqueue({.kind = QSshSftpSessionPrivate::Rename, .path = oldPath, .secondPath = newPath})
               : 0;
}

quint64 QSshSftpSession::createSymbolicLink(const QString &targetPath, const QString &linkPath) {
    Q_D(QSshSftpSession);
    return validPath(targetPath) && validPath(linkPath)
               ? d->enqueue({.kind = QSshSftpSessionPrivate::Symlink, .path = targetPath, .secondPath = linkPath})
               : 0;
}

quint64 QSshSftpSession::setPermissions(const QString &path, const quint32 permissions) {
    Q_D(QSshSftpSession);
    return validPath(path) && !(permissions & ~static_cast<quint32>(07777))
               ? d->enqueue({.kind = QSshSftpSessionPrivate::Chmod, .path = path, .value = permissions})
               : 0;
}

quint64 QSshSftpSession::openFile(const QString &path, OpenFlags flags, QFileDevice::Permissions permissions) {
    return qssh_sftp::validPermissions(permissions) ? openFile(path, flags, qssh_sftp::permissionsToMode(permissions))
                                                    : 0;
}

quint64 QSshSftpSession::makeDirectory(const QString &path, QFileDevice::Permissions permissions) {
    return qssh_sftp::validPermissions(permissions) ? makeDirectory(path, qssh_sftp::permissionsToMode(permissions))
                                                    : 0;
}

quint64 QSshSftpSession::setPermissions(const QString &path, QFileDevice::Permissions permissions) {
    return qssh_sftp::validPermissions(permissions) ? setPermissions(path, qssh_sftp::permissionsToMode(permissions))
                                                    : 0;
}
