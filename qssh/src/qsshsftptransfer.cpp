#include "qsshclient_p.h"
#include "qsshsession_p.h"
#include "qsshsftppermissions_p.h"
#include "qsshsftptransfer_p.h"

#include <QFile>
#include <QSaveFile>
#include <QTemporaryFile>
#include <QThread>
#include <algorithm>
#include <limits>

namespace {
    using Transfer = QSshSftpTransfer;
    using Sftp = QSshSftpSession;
    constexpr qint64 ChunkSize = 65536; ///< 与公开分块读写能力保持一致。

    /** 在首次调度前校验 token，避免未启动协程访问已销毁 Private。 */
    asio::awaitable<void> guarded(std::shared_ptr<bool> token, asio::awaitable<void> operation) {
        if (*token) {
            co_await std::move(operation);
        }
    }

    /** 为无人接收的句柄安排关闭及可选删除，不捕获已销毁的传输。 */
    void cleanup(QSshSftpSessionPrivate *sftp, quint64 handle, QString path = {}, bool remove = false) {
        sftp->enqueueInternal(
            {.kind = QSshSftpSessionPrivate::Close, .handle = handle}, [sftp, token = sftp->alive, path, remove](auto) {
                if (*token && !sftp->terminated && remove) {
                    sftp->enqueueInternal({.kind = QSshSftpSessionPrivate::Remove, .path = path}, [](auto) {});
                }
            });
    }
} // namespace

QSshSftpDeviceBridge::QSshSftpDeviceBridge(QIODevice *source, asio::any_io_executor worker)
    : device(source),
      executor(std::move(worker)) {
    if (source) {
        moveToThread(source->thread());
    }
}

void QSshSftpDeviceBridge::deliver(Callback callback, Result result) {
    std::lock_guard lock(dispatchMutex);
    if (!stopped && callback) {
        asio::post(executor, [callback = std::move(callback), result = std::move(result)]() mutable {
            callback(std::move(result));
        });
    }
}

void QSshSftpDeviceBridge::prepare(bool upload, Callback callback) {
    QMetaObject::invokeMethod(
        this,
        [this, upload, callback = std::move(callback)]() mutable {
            if (stopped) {
                return;
            }
            if (!device || !device->isOpen() || (upload ? !device->isReadable() : !device->isWritable())) {
                deliver(std::move(callback),
                        {.error = QStringLiteral("QIODevice is not open in the required direction")});
                return;
            }
            connect(device, &QIODevice::readyRead, this, [this] { drainRead(); });
            connect(device, &QIODevice::bytesWritten, this, [this](qint64) { drainWrite(); });
            connect(device, &QIODevice::readChannelFinished, this, [this] {
                ended = true;
                drainRead();
            });
            connect(device, &QIODevice::aboutToClose, this,
                    [this] { fail(QStringLiteral("QIODevice closed during transfer")); });
            connect(device, &QObject::destroyed, this,
                    [this] { fail(QStringLiteral("QIODevice destroyed during transfer")); });
            deliver(std::move(callback),
                    {.total = upload && !device->isSequential() ? device->size() - device->pos() : -1});
        },
        Qt::QueuedConnection);
}

void QSshSftpDeviceBridge::read(qint64 size, Callback callback) {
    QMetaObject::invokeMethod(
        this,
        [this, size, callback = std::move(callback)]() mutable {
            if (stopped) {
                return;
            }
            readSize = size;
            reader = std::move(callback);
            drainRead();
        },
        Qt::QueuedConnection);
}

void QSshSftpDeviceBridge::drainRead() {
    if (stopped || !reader) {
        return;
    }
    if (!device || !device->isOpen()) {
        fail(QStringLiteral("Upload source disappeared or closed"));
        return;
    }
    QByteArray data(static_cast<int>(readSize), Qt::Uninitialized);
    const auto count = device->read(data.data(), readSize);
    if (count < 0) {
        fail(device->errorString());
        return;
    }
    data.resize(static_cast<int>(count));
    const bool eof = count == 0 && (device->isSequential() ? ended : device->atEnd());
    if (count || eof) {
        auto callback = std::move(reader);
        reader = {};
        deliver(std::move(callback), {.data = data, .eof = eof});
    }
}

void QSshSftpDeviceBridge::write(QByteArray data, Callback callback) {
    QMetaObject::invokeMethod(
        this,
        [this, data = std::move(data), callback = std::move(callback)]() mutable {
            if (stopped) {
                return;
            }
            output = std::move(data);
            outputOffset = 0;
            writer = std::move(callback);
            drainWrite();
        },
        Qt::QueuedConnection);
}

void QSshSftpDeviceBridge::drainWrite() {
    if (stopped || !writer || writing) {
        return;
    }
    if (!device || !device->isOpen()) {
        fail(QStringLiteral("Download destination disappeared or closed"));
        return;
    }
    writing = true;
    while (outputOffset < output.size()) {
        const auto count = device->write(output.constData() + outputOffset, output.size() - outputOffset);
        if (count < 0) {
            writing = false;
            fail(device->errorString());
            return;
        }
        if (!count) {
            writing = false;
            return;
        }
        outputOffset += count;
    }
    writing = false;
    if (device->bytesToWrite() == 0) {
        output.clear();
        auto callback = std::move(writer);
        writer = {};
        deliver(std::move(callback), {});
    }
}

void QSshSftpDeviceBridge::fail(const QString &message) {
    auto read = std::move(reader), write = std::move(writer);
    reader = {};
    writer = {};
    deliver(std::move(read), {.error = message});
    deliver(std::move(write), {.error = message});
}

void QSshSftpDeviceBridge::finishInput() {
    QMetaObject::invokeMethod(
        this,
        [this] {
            ended = true;
            drainRead();
        },
        Qt::QueuedConnection);
}

void QSshSftpDeviceBridge::stop() {
    {
        std::lock_guard lock(dispatchMutex);
        stopped = true;
        executor = {};
    }
    // deleteLater 可跨线程调用；即使设备线程随后退出，也能由 Qt 回收延迟删除对象。
    deleteLater();
}

QSshSftpTransferPrivate::QSshSftpTransferPrivate(QSshSftpTransfer *owner, QSshSftpSession *parent, bool direction,
                                                 QString remote, QString local, QIODevice *device,
                                                 Transfer::Options configuration)
    : q(owner),
      sftp(parent->d_func()),
      upload(direction),
      remotePath(std::move(remote)),
      localPath(std::move(local)),
      options(configuration),
      event(sftp->session->client->io),
      idle(sftp->session->client->io) {
    if (device) {
        bridge = new QSshSftpDeviceBridge(device, sftp->session->client->io.get_executor());
    }
    sftp->session->client->synchronize([this] { sftp->transfers.push_back(this); });
    submit([this] { launch(doStart()); });
}

QSshSftpTransferPrivate::~QSshSftpTransferPrivate() {
    sftp->session->client->synchronize([this] {
        finish(Transfer::Cancelled, Transfer::OperationCancelled, QStringLiteral("Transfer destroyed"));
        *alive = false;
        std::erase(sftp->transfers, this);
    });
}

void QSshSftpTransferPrivate::submit(std::function<void()> task) {
    asio::post(sftp->session->client->io, [token = alive, task = std::move(task)] {
        if (*token) {
            task();
        }
    });
}

void QSshSftpTransferPrivate::launch(asio::awaitable<void> operation) {
    asio::co_spawn(sftp->session->client->io, guarded(alive, std::move(operation)),
                   [this, token = alive](std::exception_ptr error) {
                       if (!*token || stopped || !error) {
                           return;
                       }
                       try {
                           std::rethrow_exception(error);
                       } catch (const std::exception &e) {
                           finish(Transfer::Failed, Transfer::RemoteError, QString::fromUtf8(e.what()));
                       } catch (...) {
                           finish(Transfer::Failed, Transfer::RemoteError, QStringLiteral("Unexpected transfer error"));
                       }
                   });
}

void QSshSftpTransferPrivate::setState(Transfer::State state) {
    QMetaObject::invokeMethod(
        q,
        [this, state, count = completed, size = total] {
            frontCompleted = count;
            frontTotal = size;
            frontState = state;
            Q_EMIT q->stateChanged(state);
        },
        Qt::QueuedConnection);
}

void QSshSftpTransferPrivate::reportProgress(bool force) {
    if (force) {
        QMetaObject::invokeMethod(
            q,
            [this, count = completed, size = total] {
                frontCompleted = count;
                frontTotal = size;
                Q_EMIT q->progress(count, size);
            },
            Qt::QueuedConnection);
        return;
    }
    {
        std::lock_guard lock(progress->mutex);
        progress->completed = completed;
        progress->total = total;
        if (progress->queued) {
            return;
        }
        progress->queued = true;
    }
    QMetaObject::invokeMethod(
        q,
        [this, snapshot = progress] {
            {
                std::lock_guard lock(snapshot->mutex);
                frontCompleted = snapshot->completed;
                frontTotal = snapshot->total;
                snapshot->queued = false;
            }
            Q_EMIT q->progress(frontCompleted, frontTotal);
        },
        Qt::QueuedConnection);
}

void QSshSftpTransferPrivate::touch() {
    idle.expires_after(std::chrono::milliseconds(options.idleTimeoutMs));
    idle.async_wait([this, token = alive](const asio::error_code &error) {
        if (*token && !error && !stopped) {
            finish(Transfer::Failed, Transfer::TimeoutError,
                   QStringLiteral("Transfer made no progress before timeout"));
        }
    });
}

asio::awaitable<bool> QSshSftpTransferPrivate::doWait() {
    const auto token = alive;
    event.expires_at(asio::steady_timer::time_point::max());
    asio::error_code ignored;
    co_await event.async_wait(asio::redirect_error(asio::use_awaitable, ignored));
    co_return *token && !stopped;
}

asio::awaitable<QSshSftpTransferPrivate::RemoteResult> QSshSftpTransferPrivate::doRemote(Request request) {
    const auto token = alive;
    auto wait = std::make_shared<Wait>(sftp->session->client->io);
    waits.insert(wait);
    auto *parent = sftp;
    const auto parentToken = sftp->alive;
    const auto cleanupPath = remotePath;
    const bool removeLateFile = upload && options.removePartialOnFailure;
    request.cancelled = [wait] { return wait->abandoned; };
    // OPEN 的迟到成功也必须回收句柄；回调只捕获独立等待对象及父级存活令牌。
    sftp->enqueueInternal(std::move(request), [wait, parent, parentAlive = sftp->alive, path = remotePath,
                                               remove = upload && options.removePartialOnFailure](RemoteResult result) {
        if (wait->abandoned && result.handle && *parentAlive && !parent->terminated) {
            cleanup(parent, result.handle, path, remove);
        }
        if (wait->abandoned) {
            return;
        }
        wait->remote = std::move(result);
        wait->done = true;
        wait->event->cancel();
    });
    while (!wait->done) {
        wait->event->expires_at(asio::steady_timer::time_point::max());
        asio::error_code ignored;
        co_await wait->event->async_wait(asio::redirect_error(asio::use_awaitable, ignored));
        if (!*token || stopped) {
            wait->abandoned = true;
            if (wait->done && wait->remote.handle && *parentToken && !parent->terminated) {
                cleanup(parent, wait->remote.handle, cleanupPath, removeLateFile);
                wait->remote.handle = 0;
            }
            co_return RemoteResult{.error = Sftp::Cancelled};
        }
    }
    waits.erase(wait);
    co_return wait->remote;
}

asio::awaitable<QSshSftpDeviceBridge::Result> QSshSftpTransferPrivate::doDevice(int kind, QByteArray data) {
    const auto token = alive;
    auto wait = std::make_shared<Wait>(sftp->session->client->io);
    waits.insert(wait);
    auto callback = [wait](QSshSftpDeviceBridge::Result result) {
        if (wait->abandoned) {
            return;
        }
        wait->device = std::move(result);
        wait->done = true;
        wait->event->cancel();
    };
    if (kind == 0) {
        bridge->prepare(upload, callback);
    } else if (kind == 1) {
        bridge->read(ChunkSize, callback);
    } else {
        bridge->write(std::move(data), callback);
    }
    while (!wait->done) {
        wait->event->expires_at(asio::steady_timer::time_point::max());
        asio::error_code ignored;
        co_await wait->event->async_wait(asio::redirect_error(asio::use_awaitable, ignored));
        if (!*token || stopped) {
            co_return QSshSftpDeviceBridge::Result{.error = QStringLiteral("Transfer stopped")};
        }
    }
    waits.erase(wait);
    co_return wait->device;
}

void QSshSftpTransferPrivate::finish(Transfer::State state, Transfer::Error error, const QString &message) {
    if (stopped) {
        return;
    }
    stopped = true;
    idle.cancel();
    event.cancel();
    for (auto &wait : waits) {
        wait->abandoned = true;
        wait->event->cancel();
        wait->event.reset();
    }
    waits.clear();
    if (bridge) {
        bridge->stop();
        bridge = nullptr;
    }
    file.reset();
    ready.clear();
    if (handle && !sftp->terminated) {
        cleanup(sftp, handle, remotePath, upload && state != Transfer::Succeeded && options.removePartialOnFailure);
        handle = 0;
    }
    reportProgress(true);
    QMetaObject::invokeMethod(
        q,
        [this, state, error, message] {
            frontError = error;
            frontErrorText = message;
            frontState = state;
            const QPointer guard(q);
            Q_EMIT q->stateChanged(state);
            if (!guard) {
                return;
            }
            if (error != Transfer::NoError && error != Transfer::OperationCancelled) {
                Q_EMIT q->errorOccurred(error);
            }
            if (guard) {
                Q_EMIT q->finished();
            }
        },
        Qt::QueuedConnection);
}

asio::awaitable<void> QSshSftpTransferPrivate::doRead(qint64 offset, qint64 length) {
    const auto token = alive;
    QByteArray data;
    while (data.size() < length) {
        auto result = co_await doRemote({.kind = QSshSftpSessionPrivate::Read,
                                         .handle = handle,
                                         .offset = static_cast<quint64>(offset + data.size()),
                                         .value = static_cast<quint32>(length - data.size())});
        if (!*token || stopped) {
            co_return;
        }
        if (result.error != Sftp::NoError) {
            finish(Transfer::Failed,
                   result.error == Sftp::SessionClosed ? Transfer::SessionClosed : Transfer::RemoteError,
                   result.message);
            co_return;
        }
        if (result.data.isEmpty()) {
            if (downloadSizeKnown && offset + data.size() < total) {
                finish(Transfer::Failed, Transfer::RemoteError,
                       QStringLiteral("Remote file ended before its advertised size"));
                co_return;
            }
            if (total < 0 || offset + data.size() < total) {
                total = offset + data.size();
            }
            break;
        }
        data.append(result.data);
        touch();
    }
    reserved -= length - data.size();
    if (!data.isEmpty()) {
        ready.emplace(offset, std::move(data));
    }
    --pending;
    event.cancel_one();
}

asio::awaitable<void> QSshSftpTransferPrivate::doWrite(qint64 offset, QByteArray data) {
    const auto token = alive;
    auto result = co_await doRemote({.kind = QSshSftpSessionPrivate::Write,
                                     .handle = handle,
                                     .offset = static_cast<quint64>(offset),
                                     .data = data});
    if (!*token || stopped) {
        co_return;
    }
    if (result.error != Sftp::NoError) {
        finish(Transfer::Failed, result.error == Sftp::SessionClosed ? Transfer::SessionClosed : Transfer::RemoteError,
               result.message);
        co_return;
    }
    completed += data.size();
    reserved -= data.size();
    --pending;
    touch();
    reportProgress();
    event.cancel_one();
}

asio::awaitable<void> QSshSftpTransferPrivate::doStart() {
    const auto token = alive;
    if (stopped) {
        co_return;
    }
    if (sftp->terminated || sftp->phase != Sftp::Ready || remotePath.isEmpty() || remotePath.contains(QChar::Null) ||
        remotePath.toUtf8().size() > 32768 || options.bufferSize < ChunkSize || options.idleTimeoutMs <= 0 ||
        !qssh_sftp::validPermissions(options.permissions) ||
        (!bridge && (localPath.isEmpty() || localPath.contains(QChar::Null)))) {
        finish(Transfer::Failed, Transfer::InvalidConfiguration,
               QStringLiteral("Invalid transfer configuration or SFTP state"));
        co_return;
    }
    touch();
    if (bridge) {
        const auto device = co_await doDevice(0);
        if (!*token || stopped) {
            co_return;
        }
        if (!device.error.isEmpty()) {
            finish(Transfer::Failed, Transfer::LocalIoError, device.error);
            co_return;
        }
        if (upload) {
            total = device.total;
        }
    } else if (upload) {
        auto source = std::make_unique<QFile>(localPath);
        if (!source->open(QIODevice::ReadOnly)) {
            finish(Transfer::Failed, Transfer::LocalIoError, source->errorString());
            co_return;
        }
        total = source->size();
        file = std::move(source);
    } else {
        if (!options.overwrite && QFile::exists(localPath)) {
            finish(Transfer::Failed, Transfer::LocalIoError, QStringLiteral("Download destination already exists"));
            co_return;
        }
        if (options.overwrite) {
            file = std::make_unique<QSaveFile>(localPath);
        } else {
            file = std::make_unique<QTemporaryFile>(localPath + QStringLiteral(".qssh-XXXXXX"));
        }
        if (!file->open(QIODevice::WriteOnly)) {
            finish(Transfer::Failed, Transfer::LocalIoError, file->errorString());
            co_return;
        }
    }
    if (!upload) {
        auto attributes = co_await doRemote({.kind = QSshSftpSessionPrivate::Stat, .path = remotePath});
        if (!*token || stopped) {
            co_return;
        }
        if (attributes.error != Sftp::NoError || (attributes.info.attributes.testFlag(Sftp::Size) &&
                                                  attributes.info.size > quint64(std::numeric_limits<qint64>::max()))) {
            finish(Transfer::Failed, Transfer::RemoteError,
                   attributes.message.isEmpty() ? QStringLiteral("Invalid remote file size") : attributes.message);
            co_return;
        }
        total = attributes.info.attributes.testFlag(Sftp::Size) ? static_cast<qint64>(attributes.info.size) : -1;
        downloadSizeKnown = total >= 0;
    }
    auto opened = co_await doRemote(
        {.kind = QSshSftpSessionPrivate::Open,
         .path = remotePath,
         .value = upload ? quint32(Sftp::Write | Sftp::Create | (options.overwrite ? Sftp::Truncate : Sftp::Exclusive))
                         : quint32(Sftp::Read),
         .mode = qssh_sftp::permissionsToMode(options.permissions)});
    if (!*token || stopped) {
        co_return;
    }
    if (opened.error != Sftp::NoError) {
        finish(Transfer::Failed, Transfer::RemoteError, opened.message);
        co_return;
    }
    handle = opened.handle;
    touch();
    setState(Transfer::Running);
    reportProgress(true);
    while (*token && !stopped) {
        if (upload) {
            if (!sourceEnded && reserved + ChunkSize <= options.bufferSize) {
                QByteArray data;
                bool eof = false;
                if (bridge) {
                    auto result = co_await doDevice(1);
                    if (!*token || stopped) {
                        co_return;
                    }
                    if (!result.error.isEmpty()) {
                        finish(Transfer::Failed, Transfer::LocalIoError, result.error);
                        co_return;
                    }
                    data = std::move(result.data);
                    eof = result.eof;
                } else {
                    data.resize(ChunkSize);
                    const auto count = file->read(data.data(), data.size());
                    if (count < 0) {
                        finish(Transfer::Failed, Transfer::LocalIoError, file->errorString());
                        co_return;
                    }
                    data.resize(static_cast<int>(count));
                    eof = count == 0;
                }
                if (!data.isEmpty()) {
                    if (nextOffset > std::numeric_limits<qint64>::max() - data.size() ||
                        (total >= 0 && data.size() > total - nextOffset)) {
                        finish(Transfer::Failed, Transfer::LocalIoError,
                               QStringLiteral("Upload source exceeds its initial size or supported offset"));
                        co_return;
                    }
                    reserved += data.size();
                    ++pending;
                    launch(doWrite(nextOffset, data));
                    nextOffset += data.size();
                    touch();
                    co_await asio::post(sftp->session->client->io, asio::use_awaitable);
                    continue;
                }
                if (eof) {
                    sourceEnded = true;
                    if (total >= 0 && total != nextOffset) {
                        finish(Transfer::Failed, Transfer::LocalIoError,
                               QStringLiteral("Upload source size changed during transfer"));
                        co_return;
                    }
                    total = nextOffset;
                    reportProgress();
                }
            }
            if (sourceEnded && !pending) {
                break;
            }
        } else {
            while (reserved + ChunkSize <= options.bufferSize && (total < 0 || nextOffset < total)) {
                const auto size = total < 0 ? ChunkSize : std::min(ChunkSize, total - nextOffset);
                reserved += size;
                ++pending;
                launch(doRead(nextOffset, size));
                nextOffset += size;
            }
            const auto entry = ready.find(writeOffset);
            if (entry != ready.end()) {
                auto data = std::move(entry->second);
                ready.erase(entry);
                if (bridge) {
                    const auto result = co_await doDevice(2, data);
                    if (!*token || stopped) {
                        co_return;
                    }
                    if (!result.error.isEmpty()) {
                        finish(Transfer::Failed, Transfer::LocalIoError, result.error);
                        co_return;
                    }
                } else {
                    qsizetype written = 0;
                    while (written < data.size()) {
                        const auto count = file->write(data.constData() + written, data.size() - written);
                        if (count <= 0) {
                            finish(Transfer::Failed, Transfer::LocalIoError, file->errorString());
                            co_return;
                        }
                        written += count;
                    }
                }
                writeOffset += data.size();
                completed += data.size();
                reserved -= data.size();
                touch();
                reportProgress();
                co_await asio::post(sftp->session->client->io, asio::use_awaitable);
                continue;
            }
            if (total >= 0 && writeOffset == total && !pending) {
                break;
            }
        }
        if (!co_await doWait()) {
            co_return;
        }
    }
    if (!*token || stopped) {
        co_return;
    }
    setState(Transfer::Finishing);
    const auto closing = handle;
    auto closed = co_await doRemote({.kind = QSshSftpSessionPrivate::Close, .handle = closing});
    if (!*token || stopped) {
        co_return;
    }
    if (closed.error != Sftp::NoError) {
        finish(Transfer::Failed, Transfer::RemoteError, closed.message);
        co_return;
    }
    handle = 0; // 收到 CLOSE 成功后才放弃所有权；等待关闭时取消仍需安排回收。
    if (!upload && file) {
        if (auto *saved = dynamic_cast<QSaveFile *>(file.get())) {
            if (!saved->commit()) {
                finish(Transfer::Failed, Transfer::LocalIoError, saved->errorString());
                co_return;
            }
        } else {
            auto *temporary = static_cast<QTemporaryFile *>(file.get());
            if (!temporary->flush()) {
                finish(Transfer::Failed, Transfer::LocalIoError, temporary->errorString());
                co_return;
            }
            temporary->close();
            if (!temporary->rename(localPath)) {
                finish(Transfer::Failed, Transfer::LocalIoError, temporary->errorString());
                co_return;
            }
            temporary->setAutoRemove(false);
        }
    }
    finish(Transfer::Succeeded, Transfer::NoError, {});
}

QSshSftpTransfer::QSshSftpTransfer(QSshSftpSession *sftp, bool upload, QString remote, QString local, QIODevice *device,
                                   const Options &options)
    : QObject(sftp),
      d_ptr(new QSshSftpTransferPrivate(this, sftp, upload, std::move(remote), std::move(local), device, options)) {
    qRegisterMetaType<State>();
    qRegisterMetaType<Error>();
}

QSshSftpTransfer::~QSshSftpTransfer() {
    Q_ASSERT(thread() == QThread::currentThread());
}

QSshSftpTransfer::State QSshSftpTransfer::state() const {
    Q_D(const QSshSftpTransfer);
    return d->frontState;
}

QSshSftpTransfer::Error QSshSftpTransfer::error() const {
    Q_D(const QSshSftpTransfer);
    return d->frontError;
}

QString QSshSftpTransfer::errorString() const {
    Q_D(const QSshSftpTransfer);
    return d->frontErrorText;
}

qint64 QSshSftpTransfer::bytesTransferred() const {
    Q_D(const QSshSftpTransfer);
    return d->frontCompleted;
}

qint64 QSshSftpTransfer::totalBytes() const {
    Q_D(const QSshSftpTransfer);
    return d->frontTotal;
}

void QSshSftpTransfer::cancel() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSftpTransfer);
    d->submit([d] { d->finish(Cancelled, OperationCancelled, QStringLiteral("Transfer cancelled")); });
}

void QSshSftpTransfer::finishInput() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSftpTransfer);
    d->submit([d] {
        if (d->bridge && d->upload && !d->stopped) {
            d->bridge->finishInput();
        }
    });
}

QSshSftpTransfer *QSshSftpSession::download(const QString &remote, const QString &local,
                                            const QSshSftpTransfer::Options &options) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new QSshSftpTransfer(this, false, remote, local, nullptr, options);
}

QSshSftpTransfer *QSshSftpSession::upload(const QString &local, const QString &remote,
                                          const QSshSftpTransfer::Options &options) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new QSshSftpTransfer(this, true, remote, local, nullptr, options);
}

QSshSftpTransfer *QSshSftpSession::download(const QString &remote, QIODevice *device,
                                            const QSshSftpTransfer::Options &options) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new QSshSftpTransfer(this, false, remote, {}, device, options);
}

QSshSftpTransfer *QSshSftpSession::upload(QIODevice *device, const QString &remote,
                                          const QSshSftpTransfer::Options &options) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new QSshSftpTransfer(this, true, remote, {}, device, options);
}
