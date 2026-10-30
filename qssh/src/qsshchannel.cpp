#include "qsshchannel_p.h"
#include "qsshclient_p.h"
#include "qsshsession_p.h"

#include <QPointer>
#include <QThread>
#include <algorithm>
#include <array>
#include <utility>

namespace {
    constexpr int MaximumBufferedBytes = 4 * 1024 * 1024; ///< 每 Channel 收、发队列各自的上限。
    constexpr int TransferChunk = 64 * 1024;              ///< 单次读写预算，使不同 Channel 公平获得调度。
} // namespace

QSshChannelPrivate::QSshChannelPrivate(QSshChannel *owner, QSshSession *session, const bool shell)
    : q(owner),
      session(session->d_func()),
      shell(shell),
      deadline(this->session->client->io) {
    this->session->client->synchronize([this] { this->session->channels.push_back(this); });
}

QSshChannelPrivate::~QSshChannelPrivate() {
    session->client->synchronize([this] {
        finish();
        *alive = false;
        std::erase(session->channels, this);
        session->schedule();
    });
}

void QSshChannelPrivate::submit(std::function<void()> task) const {
    asio::post(session->client->io, [token = alive, task = std::move(task)] {
        if (*token) {
            task();
        }
    });
}

void QSshChannelPrivate::notify(std::function<void()> task) const {
    QMetaObject::invokeMethod(q, std::move(task), Qt::QueuedConnection);
}

void QSshChannelPrivate::setState(QSshChannel::State value) {
    phase = value;
    notify([this, value] {
        if (frontState == value || (frontClosing && value != QSshChannel::Closing && value != QSshChannel::Closed)) {
            return;
        }
        frontState = value;
        const QPointer guard(q);
        Q_EMIT q->stateChanged(value);
        if (!guard) {
            return;
        }
        if (value == QSshChannel::Open && !frontClosing) {
            Q_EMIT q->opened();
        } else if (value == QSshChannel::Closed) {
            Q_EMIT q->finished();
        }
    });
}

void QSshChannelPrivate::setDeadline() {
    deadline.expires_after(std::chrono::milliseconds(session->options.channelTimeoutMs));
    deadline.async_wait([this, token = alive](const asio::error_code &error) {
        if (*token && !error && phase != QSshChannel::Closed) {
            fail(QSshChannel::TimeoutError, QStringLiteral("SSH channel operation timed out"));
            session->schedule();
        }
    });
}

void QSshChannelPrivate::open(const QString &argument) {
    Q_ASSERT(q->thread() == QThread::currentThread());
    if (frontState != QSshChannel::Idle || frontClosing) {
        return;
    }
    const bool valid = session->frontState == QSshSession::Ready && !argument.isEmpty() &&
                       !argument.contains(QChar::Null);
    frontState = QSshChannel::Opening;
    const QSize size = frontSize;
    submit([this, argument, size, valid] {
        if (phase == QSshChannel::Closed) {
            return;
        }
        if (!valid || session->phase != QSshSession::Ready || session->terminated) {
            fail(QSshChannel::InvalidState,
                 QStringLiteral("Opening a channel requires a ready session and valid arguments"));
            return;
        }
        phase = QSshChannel::Opening;
        openArgument = argument.toUtf8();
        requestedSize = size;
        handle = ssh_channel_new(session->handle);
        if (!handle) {
            fail(QSshChannel::OpenError, session->sshError());
            return;
        }
        callbacks.userdata = this;
        // 回调只记录状态，句柄的释放和前端通知统一在 advance()/finish() 中完成。
        callbacks.channel_close_function = [](ssh_session, ssh_channel, void *userdata) {
            static_cast<QSshChannelPrivate *>(userdata)->remoteClosed = true;
        };
        callbacks.channel_exit_status_function = [](ssh_session, ssh_channel, const int status, void *userdata) {
            auto *d = static_cast<QSshChannelPrivate *>(userdata);
            d->hasCode = true;
            d->code = status;
        };
        callbacks.channel_exit_signal_function = [](ssh_session, ssh_channel, const char *signal, int, const char *,
                                                    const char *, void *userdata) {
            static_cast<QSshChannelPrivate *>(userdata)->signal = QString::fromUtf8(signal);
        };
        ssh_callbacks_init(&callbacks);
        if (ssh_set_channel_callbacks(handle, &callbacks) != SSH_OK) {
            fail(QSshChannel::OpenError, session->sshError());
            return;
        }
        setDeadline();
        session->schedule();
    });
    Q_EMIT q->stateChanged(QSshChannel::Opening);
}

bool QSshChannelPrivate::advance() {
    if (!handle || phase == QSshChannel::Closed) {
        return false;
    }
    if (phase == QSshChannel::Opening) {
        int result = SSH_ERROR;
        if (openStep == 0) {
            result = ssh_channel_open_session(handle);
        } else if (openStep == 1 && session->options.agentForwarding) {
            result = ssh_channel_request_auth_agent(handle);
            if (result == SSH_OK) {
                session->agentRequested = true;
            }
        } else if (openStep == 2 && session->options.x11.enabled) {
            const auto cookie = session->x11FakeCookie.toHex();
            result = ssh_channel_request_x11(handle, session->options.x11.singleConnection, "MIT-MAGIC-COOKIE-1",
                                             cookie.constData(), session->x11Screen);
            if (result == SSH_OK) {
                session->x11Requested = true;
            }
        } else if (openStep == 3 && shell) {
            // PTY 请求在重试时使用同一尺寸；后续 resize 在打开后单独发送。
            if (resizingSize.isEmpty()) {
                resizingSize = requestedSize;
            }
            result = ssh_channel_request_pty_size(handle, openArgument.constData(), resizingSize.width(),
                                                  resizingSize.height());
        } else if (openStep == 4) {
            result = shell ? ssh_channel_request_shell(handle)
                           : ssh_channel_request_exec(handle, openArgument.constData());
        } else {
            result = SSH_OK; // 未启用的可选请求只推进阶段，不发送网络报文。
        }
        if (result == SSH_AGAIN) {
            return false;
        }
        if (result != SSH_OK) {
            fail(QSshChannel::OpenError, session->sshError());
            return true;
        }
        ++openStep;
        if (shell && openStep == 4) {
            appliedSize = resizingSize;
            resizingSize = {};
        }
        if (openStep == 5) {
            deadline.cancel();
            {
                std::lock_guard lock(bufferMutex);
                acceptingInput = true;
            }
            setState(QSshChannel::Open);
        }
        return true;
    }

    bool progress = receive(false);
    if (!handle) {
        return true;
    }
    progress = receive(true) || progress;
    if (!handle) {
        return true;
    }
    if (ssh_channel_is_eof(handle) && !eofNotified) {
        eofNotified = true;
        notify([this] { Q_EMIT q->readEof(); });
    }
    // 远端关闭可能与最后一批数据同一包到达，必须等两条流的 libssh 缓冲都排空。
    if (remoteClosed) {
        if (ssh_channel_poll(handle, 0) <= 0 && ssh_channel_poll(handle, 1) <= 0) {
            finish();
            return true;
        }
        return progress;
    }

    QByteArray input;
    {
        std::lock_guard lock(bufferMutex);
        input = inputBuffer.left(TransferChunk);
    }
    if (!input.isEmpty() && !closeSent) {
        const int result = ssh_channel_write(handle, input.constData(), static_cast<uint32_t>(input.size()));
        if (result == SSH_ERROR) {
            fail(QSshChannel::WriteError, session->sshError());
            return true;
        }
        if (result > 0) {
            {
                std::lock_guard lock(bufferMutex);
                inputBuffer.remove(0, result);
                writtenPending += result;
            }
            notifyData();
            progress = true;
        }
    }
    bool inputEmpty;
    {
        std::lock_guard lock(bufferMutex);
        inputEmpty = inputBuffer.isEmpty();
    }
    if (inputEmpty && eofRequested && !eofSent) {
        const int result = ssh_channel_send_eof(handle);
        if (result == SSH_ERROR) {
            fail(QSshChannel::WriteError, session->sshError());
            return true;
        }
        if (result == SSH_OK) {
            eofSent = true;
            progress = true;
        }
    }
    if (inputEmpty && eofSent && closeRequested && !closeSent) {
        const int result = ssh_channel_close(handle);
        if (result == SSH_ERROR) {
            fail(QSshChannel::WriteError, session->sshError());
            return true;
        }
        if (result == SSH_OK) {
            closeSent = true;
            progress = true;
        }
    }
    if (shell && !closeRequested && requestedSize != appliedSize) {
        if (resizingSize.isEmpty()) {
            resizingSize = requestedSize;
        }
        if (const int result = ssh_channel_change_pty_size(handle, resizingSize.width(), resizingSize.height());
            result != SSH_AGAIN) {
            appliedSize = resizingSize;
            resizingSize = {};
            if (result != SSH_OK) {
                fail(QSshChannel::ResizeError, session->sshError(), false);
            }
            progress = true;
        }
    }
    return progress;
}

bool QSshChannelPrivate::receive(const bool stderrStream) {
    int capacity;
    {
        std::lock_guard lock(bufferMutex);
        capacity = MaximumBufferedBytes - stdoutBuffer.size() - stderrBuffer.size();
    }
    if (capacity <= 0) {
        // 不继续消耗该 Channel 的远端窗口；Session 仍可处理其他通道及协议消息。
        return false;
    }
    std::array<char, TransferChunk> data = {};
    const int count = ssh_channel_read_nonblocking(handle, data.data(), std::min(capacity, TransferChunk),
                                                   stderrStream);
    if (count == SSH_ERROR) {
        fail(QSshChannel::ReadError, session->sshError());
        return false;
    }
    if (count <= 0) {
        return false;
    }
    {
        std::lock_guard lock(bufferMutex);
        (stderrStream ? stderrBuffer : stdoutBuffer).append(data.data(), count);
        (stderrStream ? stderrPending : stdoutPending) = true;
    }
    notifyData();
    return true;
}

void QSshChannelPrivate::notifyData() {
    {
        std::lock_guard lock(bufferMutex);
        if (notificationPending) {
            return;
        }
        notificationPending = true;
    }
    notify([this] {
        bool out;
        bool err;
        qint64 written;
        {
            std::lock_guard lock(bufferMutex);
            out = std::exchange(stdoutPending, false);
            err = std::exchange(stderrPending, false);
            written = std::exchange(writtenPending, 0);
            notificationPending = false;
        }
        const QPointer guard(q);
        if (out) {
            Q_EMIT q->readyRead();
        }
        if (guard && err) {
            Q_EMIT q->readyReadStandardError();
        }
        if (guard && written) {
            Q_EMIT q->bytesWritten(written);
        }
    });
}

void QSshChannelPrivate::fail(QSshChannel::Error error, const QString &message, const bool fatal) {
    notify([this, error, message] {
        frontError = error;
        frontErrorText = message;
        Q_EMIT q->errorOccurred(error);
    });
    if (fatal) {
        finish();
    }
}

void QSshChannelPrivate::finish() {
    if (phase == QSshChannel::Closed) {
        return;
    }
    deadline.cancel();
    if (handle) {
        ssh_remove_channel_callbacks(handle, &callbacks);
        ssh_channel_free(handle);
        handle = nullptr;
    }
    {
        std::lock_guard lock(bufferMutex);
        acceptingInput = false;
        inputBuffer.clear();
    }
    const bool receivedCode = hasCode;
    const int exitCode = code;
    const QString exitSignal = signal;
    notify([this, receivedCode, exitCode, exitSignal] {
        frontHasExitCode = receivedCode;
        frontExitCode = exitCode;
        frontExitSignal = exitSignal;
    });
    setState(QSshChannel::Closed);
}

QByteArray QSshChannelPrivate::take(const bool stderrStream) {
    QByteArray result;
    {
        std::lock_guard lock(bufferMutex);
        result.swap(stderrStream ? stderrBuffer : stdoutBuffer);
    }
    if (!result.isEmpty()) {
        submit([this] { session->schedule(); });
    }
    return result;
}

QSshChannel::QSshChannel(QSshSession *session, const bool shell)
    : QObject(session),
      d_ptr(new QSshChannelPrivate(this, session, shell)) {
    qRegisterMetaType<State>();
    qRegisterMetaType<Error>();
}

QSshChannel::~QSshChannel() {
    Q_ASSERT(thread() == QThread::currentThread());
}

QSshChannel::State QSshChannel::state() const {
    Q_D(const QSshChannel);
    return d->frontState;
}

QSshChannel::Error QSshChannel::error() const {
    Q_D(const QSshChannel);
    return d->frontError;
}

QString QSshChannel::errorString() const {
    Q_D(const QSshChannel);
    return d->frontErrorText;
}

QByteArray QSshChannel::readAll() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshChannel);
    return d->take(false);
}

QByteArray QSshChannel::readAllStandardError() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshChannel);
    return d->take(true);
}

qint64 QSshChannel::bytesAvailable() const {
    Q_D(const QSshChannel);
    std::lock_guard lock(d->bufferMutex);
    return d->stdoutBuffer.size() + d->stderrBuffer.size();
}

qint64 QSshChannel::bytesToWrite() const {
    Q_D(const QSshChannel);
    std::lock_guard lock(d->bufferMutex);
    return d->inputBuffer.size();
}

bool QSshChannel::hasExitCode() const {
    Q_D(const QSshChannel);
    return d->frontHasExitCode;
}

int QSshChannel::exitCode() const {
    Q_D(const QSshChannel);
    return d->frontExitCode;
}

QString QSshChannel::exitSignal() const {
    Q_D(const QSshChannel);
    return d->frontExitSignal;
}

qint64 QSshChannel::write(const QByteArray &data) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshChannel);
    if (d->frontState != Open || d->frontClosing || d->frontEof) {
        d->frontError = InvalidState;
        d->frontErrorText = QStringLiteral("Channel is not writable");
        Q_EMIT errorOccurred(InvalidState);
        return -1;
    }
    Error failure = NoError;
    {
        std::lock_guard lock(d->bufferMutex);
        if (!d->acceptingInput) {
            failure = InvalidState;
        } else if (data.size() <= MaximumBufferedBytes - d->inputBuffer.size()) {
            d->inputBuffer.append(data);
        } else {
            failure = BufferFull;
        }
    }
    if (failure != NoError) {
        // 不在持有缓冲锁时发信号，槽函数可能立即查询 bytesToWrite() 或删除对象。
        d->frontError = failure;
        d->frontErrorText = failure == BufferFull ? QStringLiteral("SSH channel send buffer is full")
                                                  : QStringLiteral("Channel is not writable");
        Q_EMIT errorOccurred(failure);
        return -1;
    }
    d->submit([d] { d->session->schedule(); });
    return data.size();
}

void QSshChannel::sendEof() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshChannel);
    if (d->frontState != Open || d->frontEof || d->frontClosing) {
        return;
    }
    d->frontEof = true;
    d->submit([d] {
        d->eofRequested = true;
        d->session->schedule();
    });
}

void QSshChannel::close() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshChannel);
    if (d->frontClosing || d->frontState == Closed) {
        return;
    }
    d->frontClosing = true;
    d->frontEof = true;
    d->submit([d] {
        if (d->phase != Open && d->phase != Closing) {
            d->finish();
        } else {
            d->closeRequested = true;
            d->eofRequested = true;
            d->setState(Closing);
            d->setDeadline();
        }
        d->session->schedule();
    });
}

void QSshChannel::abort() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshChannel);
    d->frontClosing = true;
    d->frontEof = true;
    d->submit([d] {
        d->finish();
        d->session->schedule();
    });
}
