#include <QThread>
#include <qvfs/private/qvirtualbackend_p.h>
#include <qvfs/qvirtualfile.h>

QVirtualFile::QVirtualFile(QVirtualFileSystem *fileSystem, QString path, QObject *parent)
    : QObject(parent),
      owner(fileSystem),
      location(std::move(path)) {}

QVirtualFile::~QVirtualFile() = default;

QString QVirtualFile::path() const {
    return location;
}

QString QVirtualFile::name() const {
    return owner ? owner->fileName(location) : QString();
}

QVirtualFileSystem *QVirtualFile::fileSystem() const {
    return owner;
}

QVirtualFile *QVirtualFile::parentFile(QObject *parent) const {
    Q_ASSERT(thread() == QThread::currentThread());
    if (!owner) {
        return nullptr;
    }
    const auto path = owner->parentPath(location);
    return path.isNull() ? nullptr : owner->file(path, parent);
}

QVirtualFile *QVirtualFile::child(const QString &name, QObject *parent) const {
    Q_ASSERT(thread() == QThread::currentThread());
    if (!owner) {
        return nullptr;
    }
    const auto path = owner->childPath(location, name);
    return path.isNull() ? nullptr : owner->file(path, parent);
}

QVirtualOpenJob *QVirtualFile::open(const QVirtualOpenOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return owner ? owner->open(location, options, parent ? parent : this)
                 : qvfs::openJob(
                       nullptr,
                       qvfs::rejectedBackend(QVirtualJob::FileSystemClosed, QStringLiteral("File system closed")),
                       options, parent ? parent : this);
}

QVirtualMutationJob *QVirtualFile::remove(const QVirtualRemoveOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    if (owner) {
        return owner->remove(location, options, parent ? parent : this);
    }
    return qvfs::mutationJob(
        [](qvfs::ControlPtr, qvfs::Callback cb) {
            cb(qvfs::failure(QVirtualJob::FileSystemClosed, QStringLiteral("File system closed")));
        },
        parent ? parent : this);
}

QVirtualMutationJob *QVirtualFile::renameTo(QVirtualFile *target, const QVirtualRenameOptions &options,
                                            QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    if (owner && target && target->fileSystem() == owner) {
        return owner->rename(location, target->path(), options, parent ? parent : this);
    }
    const auto error = owner ? QVirtualJob::CrossFileSystem : QVirtualJob::FileSystemClosed;
    return qvfs::mutationJob(
        [error](qvfs::ControlPtr, qvfs::Callback cb) {
            cb(qvfs::failure(error, QStringLiteral("Rename requires a live shared file system")));
        },
        parent ? parent : this);
}

QVirtualTransferJob *QVirtualFile::copyTo(QVirtualFile *target, const QVirtualTransferOptions &options,
                                          QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return owner ? owner->copy(location, target, options, parent ? parent : this)
                 : qvfs::failedTransfer(QVirtualJob::FileSystemClosed, QStringLiteral("File system closed"),
                                        parent ? parent : this);
}

QVirtualTransferJob *QVirtualFile::moveTo(QVirtualFile *target, const QVirtualTransferOptions &options,
                                          QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return owner ? owner->move(location, target, options, parent ? parent : this)
                 : qvfs::failedTransfer(QVirtualJob::FileSystemClosed, QStringLiteral("File system closed"),
                                        parent ? parent : this);
}
