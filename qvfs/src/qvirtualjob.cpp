#include <QThread>
#include <condition_variable>
#include <mutex>
#include <qvfs/qvirtualjob.h>

struct QVirtualJob::Context {
    std::mutex mutex;
    std::condition_variable consumed;
    QVirtualJob *receiver = nullptr;
    bool stopped = false;
    quint64 posted = 0;
    quint64 delivered = 0;
};

QVirtualJob::QVirtualJob(QObject *parent) : QObject(parent), delivery(std::make_shared<Context>()) {
    delivery->receiver = this;
    qRegisterMetaType<QVirtualFileInfo>();
    qRegisterMetaType<QList<QVirtualFileInfo>>();
}

QVirtualJob::~QVirtualJob() {
    Q_ASSERT(thread() == QThread::currentThread());
    {
        std::lock_guard lock(delivery->mutex);
        delivery->receiver = nullptr;
        delivery->stopped = true;
    }
    delivery->consumed.notify_all();
}

QVirtualJob::State QVirtualJob::state() const {
    return currentState;
}

bool QVirtualJob::isFinished() const {
    return currentState != Pending;
}

QVirtualJob::Error QVirtualJob::error() const {
    return currentError;
}

QString QVirtualJob::errorString() const {
    return currentMessage;
}

int QVirtualJob::backendError() const {
    return currentBackendError;
}

bool QVirtualJob::isCancellationRequested() const {
    return cancellationRequested;
}

QVirtualJob::ContextPtr QVirtualJob::context() const {
    return delivery;
}

bool QVirtualJob::isStopped(const ContextPtr &context) {
    std::lock_guard lock(context->mutex);
    return context->stopped;
}

bool QVirtualJob::deliver(const ContextPtr &context, std::function<void(QVirtualJob *)> callback, bool wait) {
    std::unique_lock lock(context->mutex);
    if (context->stopped || !context->receiver) {
        return false;
    }
    const auto sequence = ++context->posted;
    QMetaObject::invokeMethod(
        context->receiver,
        [context, sequence, callback = std::move(callback)] {
            QVirtualJob *receiver = nullptr;
            {
                std::lock_guard guard(context->mutex);
                if (!context->stopped) {
                    receiver = context->receiver;
                }
            }
            if (receiver) {
                callback(receiver);
            }
            {
                std::lock_guard guard(context->mutex);
                context->delivered = qMax(context->delivered, sequence);
            }
            context->consumed.notify_all();
        },
        Qt::QueuedConnection);
    if (wait) {
        context->consumed.wait(lock, [&] { return context->stopped || context->delivered >= sequence; });
    }
    return !context->stopped;
}

void QVirtualJob::finish(Error error, QString message, int backendError) {
    Q_ASSERT(thread() == QThread::currentThread());
    if (isFinished()) {
        return;
    }
    currentError = error;
    currentMessage = std::move(message);
    currentBackendError = backendError;
    currentState = error == NoError ? Succeeded : error == OperationCancelled ? Cancelled : Failed;
    {
        std::lock_guard lock(delivery->mutex);
        delivery->stopped = true;
    }
    delivery->consumed.notify_all();
    Q_EMIT finished();
}

void QVirtualJob::cancel() {
    Q_ASSERT(thread() == QThread::currentThread());
    if (isFinished() || cancellationRequested) {
        return;
    }
    cancellationRequested = true;
    requestCancel();
}

void QVirtualJob::requestCancel() {
    finish(OperationCancelled, QStringLiteral("Operation cancelled"));
}

QVirtualMutationJob::QVirtualMutationJob(QObject *parent) : QVirtualJob(parent) {}

quint64 QVirtualMutationJob::affectedCount() const {
    return affected;
}

QString QVirtualMutationJob::failedPath() const {
    return failed;
}

void QVirtualMutationJob::setResult(quint64 count, QString path) {
    affected = count;
    failed = std::move(path);
}

QVirtualStatJob::QVirtualStatJob(QObject *parent) : QVirtualJob(parent) {}

QVirtualFileInfo QVirtualStatJob::info() const {
    return result;
}

void QVirtualStatJob::setInfo(QVirtualFileInfo info) {
    result = std::move(info);
}

QVirtualListJob::QVirtualListJob(QObject *parent) : QVirtualJob(parent) {}

quint64 QVirtualListJob::entriesCount() const {
    return count;
}

void QVirtualListJob::publish(const QList<QVirtualFileInfo> &entries) {
    if (isFinished() || entries.isEmpty()) {
        return;
    }
    count += entries.size();
    Q_EMIT entriesReady(entries);
}
