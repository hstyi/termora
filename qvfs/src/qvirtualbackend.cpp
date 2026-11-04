#include <QRunnable>
#include <QThreadPool>
#include <qvfs/private/qvirtualbackend_p.h>
#include <qvfs/qvirtualfilesystem.h>

namespace qvfs {
    void SerialQueue::post(std::function<void()> task) {
        std::lock_guard lock(mutex);
        tasks.push_back(std::move(task));
        if (running) {
            return;
        }
        running = true;
        QThreadPool::globalInstance()->start(QRunnable::create([self = shared_from_this()] { self->drain(); }));
    }

    void SerialQueue::drain() {
        for (int i = 0; i < 64; ++i) {
            std::function<void()> task;
            {
                std::lock_guard lock(mutex);
                if (tasks.empty()) {
                    running = false;
                    return;
                }
                task = std::move(tasks.front());
                tasks.pop_front();
            }
            task();
        }
        QThreadPool::globalInstance()->start(QRunnable::create([self = shared_from_this()] { self->drain(); }));
    }

    Result failure(QVirtualJob::Error code, QString message) {
        Result result;
        result.error = code;
        result.message = std::move(message);
        return result;
    }

    QString validateOpen(const QVirtualOpenOptions &o) {
        using O = QVirtualOpenOptions;
        if (o.access < O::ReadOnly || o.access > O::ReadWrite || o.creation < O::OpenExisting ||
            o.creation > O::Replace) {
            return QStringLiteral("Invalid open mode");
        }
        if (o.access == O::ReadOnly && (o.creation != O::OpenExisting || o.append || o.atomicReplace)) {
            return QStringLiteral("Read-only open cannot create, append or replace");
        }
        if (o.atomicReplace && (o.access != O::WriteOnly || o.creation != O::Replace || o.append)) {
            return QStringLiteral("Atomic replacement requires WriteOnly + Replace without Append");
        }
        return {};
    }

    class RejectedBackend final : public Backend {
    public:
        RejectedBackend(QVirtualJob::Error error, QString message) : result(failure(error, std::move(message))) {}

        const void *identity() const override {
            return this;
        }

        QString path() const override {
            return {};
        }

        void open(QVirtualOpenOptions, ControlPtr, Callback cb) override {
            cb(result);
        }

        void remove(QVirtualRemoveOptions, ControlPtr, Callback cb) override {
            cb(result);
        }

        void rename(QString, QVirtualRenameOptions, ControlPtr, Callback cb) override {
            cb(result);
        }

    private:
        Result result; ///< 返回同一失败快照，调用方负责 Qt 异步交付。
    };

    std::shared_ptr<Backend> rejectedBackend(QVirtualJob::Error error, QString message) {
        return std::make_shared<RejectedBackend>(error, std::move(message));
    }

    class MutationJob final : public QVirtualMutationJob {
    public:
        MutationJob(std::function<void(ControlPtr, Callback)> start, QObject *parent) : QVirtualMutationJob(parent) {
            const auto ctx = context();
            deliver(ctx, [start = std::move(start), ctx, control = control](QVirtualJob *) {
                start(control, [ctx](Result result) {
                    deliver(ctx, [result = std::move(result)](QVirtualJob *base) {
                        auto *job = static_cast<MutationJob *>(base);
                        job->setResult(result.affected, result.failedPath);
                        job->finish(result.error, result.message, result.nativeError);
                    });
                });
            });
        }

        ~MutationJob() override {
            control->cancelled = true;
        }

    protected:
        void requestCancel() override {
            control->cancelled = true;
        }

    private:
        ControlPtr control = std::make_shared<Control>(); ///< 独立于前端对象存活。
    };

    QVirtualMutationJob *mutationJob(std::function<void(ControlPtr, Callback)> start, QObject *parent) {
        return new MutationJob(std::move(start), parent);
    }

    class OpenJob final : public QVirtualOpenJob {
    public:
        OpenJob(QVirtualFileSystem *owner, std::shared_ptr<Backend> backend, QVirtualOpenOptions options,
                QObject *parent)
            : QVirtualOpenJob(owner, parent) {
            start(std::move(backend), options);
        }
    };

    QVirtualOpenJob *openJob(QVirtualFileSystem *owner, std::shared_ptr<Backend> backend, QVirtualOpenOptions options,
                             QObject *parent) {
        return new OpenJob(owner, std::move(backend), options, parent);
    }

    class FailedTransfer final : public QVirtualTransferJob {
    public:
        FailedTransfer(QVirtualJob::Error error, QString message, QObject *parent) : QVirtualTransferJob(parent) {
            deliver(context(), [error, message = std::move(message)](QVirtualJob *base) {
                static_cast<FailedTransfer *>(base)->finish(error, message);
            });
        }
    };

    QVirtualTransferJob *failedTransfer(QVirtualJob::Error error, QString message, QObject *parent) {
        return new FailedTransfer(error, std::move(message), parent);
    }
} // namespace qvfs
