#include <QPointer>
#include <QThread>
#include <limits>
#include <optional>
#include <qvfs/private/qvirtualbackend_p.h>
#include <qvfs/qvirtualfilehandle.h>
#include <qvfs/qvirtualfilesystem.h>

using namespace qvfs;

QVirtualReadJob::QVirtualReadJob(QObject *parent) : QVirtualJob(parent), control(std::make_shared<Control>()) {}

QVirtualReadJob::~QVirtualReadJob() {
    control->cancelled = true;
}

QByteArray QVirtualReadJob::data() const {
    return contents;
}

bool QVirtualReadJob::atEnd() const {
    return eof;
}

void QVirtualReadJob::requestCancel() {
    control->cancelled = true;
    QVirtualJob::requestCancel();
}

QVirtualWriteJob::QVirtualWriteJob(QObject *parent) : QVirtualJob(parent), control(std::make_shared<Control>()) {}

QVirtualWriteJob::~QVirtualWriteJob() {
    control->cancelled = true;
}

qint64 QVirtualWriteJob::bytesWritten() const {
    return written;
}

void QVirtualWriteJob::requestCancel() {
    control->cancelled = true;
}

QVirtualFileHandle::QVirtualFileHandle(std::shared_ptr<Handle> backend, qint64 size, QObject *parent)
    : QObject(parent),
      backend(std::move(backend)),
      size(size) {}

QVirtualFileHandle::~QVirtualFileHandle() {
    if (!closing) {
        backend->close(false, [](Result) {});
    }
}

bool QVirtualFileHandle::isClosing() const {
    return closing;
}

qint64 QVirtualFileHandle::initialSize() const {
    return size;
}

QVirtualReadJob *QVirtualFileHandle::readAt(quint64 offset, quint32 count, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    auto *job = new QVirtualReadJob(parent ? parent : this);
    const auto ctx = job->context();
    const auto done = [ctx](Result result) {
        QVirtualReadJob::deliver(ctx, [result = std::move(result)](QVirtualJob *base) {
            auto *read = static_cast<QVirtualReadJob *>(base);
            read->contents = result.data;
            read->eof = result.eof;
            read->finish(result.error, result.message, result.nativeError);
        });
    };
    if (closing || !count || count > 65536 || offset > std::numeric_limits<quint64>::max() - count) {
        done(failure(QVirtualJob::InvalidArgument, QStringLiteral("Closed handle or invalid read range")));
    } else {
        backend->read(offset, count, job->control, done);
    }
    return job;
}

QVirtualWriteJob *QVirtualFileHandle::writeAt(quint64 offset, QByteArray data, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    auto *job = new QVirtualWriteJob(parent ? parent : this);
    const auto ctx = job->context();
    const auto done = [ctx](Result result) {
        QVirtualWriteJob::deliver(ctx, [result = std::move(result)](QVirtualJob *base) {
            auto *write = static_cast<QVirtualWriteJob *>(base);
            write->written = result.error == QVirtualJob::NoError ? static_cast<qint64>(result.affected) : 0;
            write->finish(result.error, result.message, result.nativeError);
        });
    };
    if (closing || data.isEmpty() || data.size() > 65536 ||
        offset > std::numeric_limits<quint64>::max() - quint64(data.size())) {
        done(failure(QVirtualJob::InvalidArgument, QStringLiteral("Closed handle or invalid write range")));
    } else {
        backend->write(offset, std::move(data), job->control, done);
    }
    return job;
}

QVirtualJob *QVirtualFileHandle::close(QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    if (closing) {
        return mutationJob(
            [](ControlPtr, Callback cb) {
                cb(failure(QVirtualJob::InvalidArgument, QStringLiteral("Handle is already closing")));
            },
            parent ? parent : this);
    }
    closing = true;

    // 立即在后端排入屏障，不能等到 Qt 事件循环再次执行才提交关闭。
    struct CloseState {
        std::mutex mutex;
        Callback callback;
        std::optional<Result> result;
    };

    auto state = std::make_shared<CloseState>();
    auto *job = mutationJob(
        [state](ControlPtr, Callback cb) {
            std::unique_lock lock(state->mutex);
            if (state->result) {
                auto result = std::move(*state->result);
                lock.unlock();
                cb(std::move(result));
            } else {
                state->callback = std::move(cb);
            }
        },
        parent ? parent : this);
    backend->close(true, [state](Result result) {
        std::unique_lock lock(state->mutex);
        if (state->callback) {
            auto cb = std::move(state->callback);
            lock.unlock();
            cb(std::move(result));
        } else {
            state->result = std::move(result);
        }
    });
    return job;
}

struct QVirtualOpenJob::Private {
    QPointer<QVirtualFileSystem> owner;               ///< 默认句柄所有者，仅在 Qt 线程访问。
    ControlPtr control = std::make_shared<Control>(); ///< 后台取消状态。
    std::shared_ptr<Handle> handle;                   ///< 尚未领取的句柄，析构时放弃。
    qint64 size = -1;                                 ///< 打开大小快照。

    ~Private() {
        control->cancelled = true;
        if (handle) {
            handle->close(false, [](Result) {});
        }
    }
};

QVirtualOpenJob::QVirtualOpenJob(QVirtualFileSystem *owner, QObject *parent)
    : QVirtualJob(parent),
      d(std::make_shared<Private>()) {
    d->owner = owner;
}

void QVirtualOpenJob::requestCancel() {
    d->control->cancelled = true;
}

void QVirtualOpenJob::start(std::shared_ptr<Backend> backend, QVirtualOpenOptions options) {
    const auto ctx = context();
    // 私有状态不能由后台回调最后释放，否则其中的 QPointer 会跨线程访问。
    deliver(ctx, [backend = std::move(backend), options, ctx](QVirtualJob *base) {
        auto *job = static_cast<QVirtualOpenJob *>(base);
        const auto done = [ctx](Result result) {
            auto pending = std::make_shared<Result>(std::move(result));
            // 无人领取的迟到 OPEN 也必须关闭；独立清理令牌的析构不依赖 Qt 事件循环。
            auto cleanup = std::shared_ptr<void>(nullptr, [pending](void *) {
                if (pending->handle) {
                    pending->handle->close(false, [](Result) {});
                }
            });
            deliver(ctx, [pending, cleanup](QVirtualJob *base) {
                auto *open = static_cast<QVirtualOpenJob *>(base);
                open->d->handle = std::move(pending->handle);
                open->d->size = pending->size;
                open->finish(pending->error, pending->message, pending->nativeError);
            });
        };
        const auto error = validateOpen(options);
        if (!error.isEmpty()) {
            done(failure(InvalidArgument, error));
        } else {
            backend->open(options, job->d->control, done);
        }
    });
}

QVirtualFileHandle *QVirtualOpenJob::takeHandle(QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    QObject *owner = parent ? parent : d->owner.data();
    if (state() != Succeeded || !d->handle || !owner) {
        return nullptr;
    }
    return new QVirtualFileHandle(std::move(d->handle), d->size, owner);
}
