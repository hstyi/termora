#include <QThread>
#include <QTimer>
#include <qvfs/private/qvirtualbackend_p.h>
#include <qvfs/qvirtualfile.h>
#include <qvfs/qvirtualfilesystem.h>

namespace {
    class ClosedStatJob final : public QVirtualStatJob {
    public:
        explicit ClosedStatJob(QObject *parent) : QVirtualStatJob(parent) {
            deliver(context(), [](QVirtualJob *job) {
                static_cast<ClosedStatJob *>(job)->finish(FileSystemClosed, QStringLiteral("File system closed"));
            });
        }
    };

    class ClosedListJob final : public QVirtualListJob {
    public:
        explicit ClosedListJob(QObject *parent) : QVirtualListJob(parent) {
            deliver(context(), [](QVirtualJob *job) {
                static_cast<ClosedListJob *>(job)->finish(FileSystemClosed, QStringLiteral("File system closed"));
            });
        }
    };

    class FileReference final : public QVirtualFile {
    public:
        FileReference(QVirtualFileSystem *fs, QString path, QObject *parent)
            : QVirtualFile(fs, std::move(path), parent) {}

        QVirtualStatJob *stat(QObject *parent) override {
            Q_ASSERT(thread() == QThread::currentThread());
            return fileSystem() ? fileSystem()->stat(path(), parent ? parent : this)
                                : new ClosedStatJob(parent ? parent : this);
        }

        QVirtualListJob *list(const QVirtualListOptions &options, QObject *parent) override {
            Q_ASSERT(thread() == QThread::currentThread());
            return fileSystem() ? fileSystem()->list(path(), options, parent ? parent : this)
                                : new ClosedListJob(parent ? parent : this);
        }
    };

    /** 移动复用 copy() 的专用通路，完整提交后才排队删除源，内容不经过 Qt 外观。 */
    class MoveJob final : public QVirtualTransferJob {
    public:
        MoveJob(QVirtualFileSystem *source, QString path, QVirtualFile *target, QVirtualTransferOptions options,
                QObject *parent)
            : QVirtualTransferJob(parent),
              source(source),
              target(target->fileSystem()),
              sourcePath(std::move(path)),
              targetPath(target->path()) {
            QTimer::singleShot(0, this, [this, options] {
                if (isFinished()) {
                    return;
                }
                if (!this->source || !this->target) {
                    finish(FileSystemClosed, QStringLiteral("Move endpoint closed"));
                    return;
                }
                std::unique_ptr<QVirtualFile> targetFile(this->target->file(targetPath));
                copy = this->source->copy(sourcePath, targetFile.get(), options, this);
                connect(copy, &QVirtualTransferJob::progress, this,
                        [this](qint64 count, qint64 size) { report(count, size, copy->phase()); });
                connect(copy, &QVirtualTransferJob::phaseChanged, this,
                        [this](Phase phase) { report(copy->bytesTransferred(), copy->totalBytes(), phase); });
                connect(copy, &QVirtualJob::finished, this, [this] {
                    if (copy->error() != NoError) {
                        finish(copy->error(), copy->errorString(), copy->backendError());
                        return;
                    }
                    const QPointer<MoveJob> guard(this);
                    report(copy->bytesTransferred(), copy->totalBytes(), Committing);
                    if (!guard) {
                        return;
                    }
                    if (!this->source || isCancellationRequested()) {
                        finish(PartialFailure, QStringLiteral("Copy completed; source retained: %1").arg(sourcePath));
                        return;
                    }
                    removal = this->source->remove(sourcePath, {}, this);
                    connect(removal, &QVirtualJob::finished, this, [this] {
                        auto error = removal->error();
                        if (error != NoError && error != OutcomeUnknown) {
                            error = PartialFailure;
                        }
                        finish(error,
                               error == NoError ? QString()
                                                : QStringLiteral("Copy completed; deleting %1 failed: %2")
                                                      .arg(sourcePath, removal->errorString()),
                               removal->backendError());
                    });
                });
            });
        }

    protected:
        void requestCancel() override {
            if (removal) {
                removal->cancel();
            } else if (copy) {
                copy->cancel();
            } else {
                QVirtualJob::requestCancel();
            }
        }

    private:
        QPointer<QVirtualFileSystem> source, target; ///< 仅在 Qt 线程访问的文件系统。
        const QString sourcePath, targetPath;        ///< 不依赖引用生命周期的路径快照。
        QVirtualTransferJob *copy = nullptr;         ///< 本任务拥有的复制子任务。
        QVirtualMutationJob *removal = nullptr;      ///< 提交复制后创建的删除子任务。
    };
} // namespace

QVirtualFileSystem::QVirtualFileSystem(QObject *parent) : QObject(parent) {}

QVirtualFileSystem::~QVirtualFileSystem() = default;

QVirtualFile *QVirtualFileSystem::file(const QString &path, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    const auto normalized = normalizePath(path);
    return normalized.isNull() ? nullptr : new FileReference(this, normalized, parent ? parent : this);
}

QVirtualFileSystem::Capabilities QVirtualFileSystem::capabilities() const {
    return {};
}

std::shared_ptr<qvfs::Backend> QVirtualFileSystem::backend(const QString &) const {
    return qvfs::rejectedBackend(QVirtualJob::Unsupported,
                                 QStringLiteral("Backend does not support content operations"));
}

QVirtualTransferJob *QVirtualFileSystem::tryCopy(QVirtualFile *, QVirtualFile *, const QVirtualTransferOptions &,
                                                 QObject *) {
    return nullptr;
}

QVirtualOpenJob *QVirtualFileSystem::open(const QString &path, const QVirtualOpenOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    const auto normalized = normalizePath(path);
    return qvfs::openJob(this,
                         normalized.isNull()
                             ? qvfs::rejectedBackend(QVirtualJob::InvalidPath, QStringLiteral("Invalid path"))
                             : backend(normalized),
                         options, parent ? parent : this);
}

QVirtualMutationJob *QVirtualFileSystem::remove(const QString &path, const QVirtualRemoveOptions &options,
                                                QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    const auto normalized = normalizePath(path);
    const auto entry = normalized.isNull()
                           ? qvfs::rejectedBackend(QVirtualJob::InvalidPath, QStringLiteral("Invalid path"))
                           : backend(normalized);
    return qvfs::mutationJob(
        [entry, options](qvfs::ControlPtr control, qvfs::Callback cb) {
            entry->remove(options, std::move(control), std::move(cb));
        },
        parent ? parent : this);
}

QVirtualMutationJob *QVirtualFileSystem::rename(const QString &source, const QString &target,
                                                const QVirtualRenameOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    const auto src = normalizePath(source), dst = normalizePath(target);
    const bool invalid = src.isNull() || dst.isNull() || parentPath(src).isNull() || parentPath(dst).isNull();
    const auto entry = invalid ? qvfs::rejectedBackend(QVirtualJob::InvalidPath,
                                                       QStringLiteral("Invalid path or root rename"))
                               : backend(src);
    return qvfs::mutationJob(
        [entry, dst, options](qvfs::ControlPtr control, qvfs::Callback cb) {
            entry->rename(dst, options, std::move(control), std::move(cb));
        },
        parent ? parent : this);
}

QVirtualTransferJob *QVirtualFileSystem::copy(const QString &source, QVirtualFile *target,
                                              const QVirtualTransferOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    QObject *owner = parent ? parent : this;
    const auto src = normalizePath(source);
    if (src.isNull() || !target || !target->fileSystem() || target->thread() != thread()) {
        return qvfs::failedTransfer(QVirtualJob::InvalidArgument, QStringLiteral("Invalid copy endpoints"), owner);
    }
    auto input = backend(src), output = target->fileSystem()->backend(target->path());
    if (input->identity() == output->identity() && input->path() == output->path()) {
        return qvfs::failedTransfer(QVirtualJob::InvalidArgument, QStringLiteral("Cannot copy a file onto itself"),
                                    owner);
    }
    // 临时源引用只用于同步选择通路，后端不得保留其裸指针。
    std::unique_ptr<QVirtualFile> reference(file(src));
    if (auto *job = tryCopy(reference.get(), target, options, owner)) {
        return job;
    }
    if (target->fileSystem() != this) {
        if (auto *job = target->fileSystem()->tryCopy(reference.get(), target, options, owner)) {
            return job;
        }
    }
    auto *job = new QVirtualTransferJob(owner);
    job->start(std::move(input), std::move(output), options);
    return job;
}

QVirtualTransferJob *QVirtualFileSystem::move(const QString &source, QVirtualFile *target,
                                              const QVirtualTransferOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    QObject *owner = parent ? parent : this;
    const auto src = normalizePath(source);
    if (src.isNull() || !target || !target->fileSystem() || target->thread() != thread()) {
        return qvfs::failedTransfer(QVirtualJob::InvalidArgument, QStringLiteral("Invalid move endpoints"), owner);
    }
    auto input = backend(src), output = target->fileSystem()->backend(target->path());
    if (input->identity() == output->identity() && input->path() == output->path()) {
        return qvfs::failedTransfer(QVirtualJob::InvalidArgument, QStringLiteral("Cannot move a file onto itself"),
                                    owner);
    }
    return new MoveJob(this, src, target, options, owner);
}
