#include "qsftpfilebackend_p.h"
#include "qsshsftptransfer_p.h"

#include <QThread>
#include <QTimer>
#include <qssh/qsftpfilesystem.h>
#include <qssh/qsshsftpsession.h>
#include <qvfs/qlocalfilesystem.h>
#include <qvfs/qvirtualfile.h>

namespace {
    using Sftp = QSshSftpSession;

    QVirtualJob::Error mapError(Sftp::Error error) {
        switch (error) {
            case Sftp::NoSuchFile:
                return QVirtualJob::NotFound;
            case Sftp::PermissionDenied:
                return QVirtualJob::PermissionDenied;
            case Sftp::Unsupported:
                return QVirtualJob::Unsupported;
            case Sftp::TimeoutError:
                return QVirtualJob::Timeout;
            case Sftp::InvalidState:
                return QVirtualJob::NotReady;
            case Sftp::NoConnection:
            case Sftp::ConnectionLost:
            case Sftp::SessionClosed:
            case Sftp::Cancelled:
                return QVirtualJob::FileSystemClosed;
            default:
                return QVirtualJob::IoError;
        }
    }

    QString nameOf(const QString &path) {
        return path == "/" ? path : path.mid(path.lastIndexOf('/') + 1);
    }

    QVirtualFileInfo snapshot(const Sftp::FileInfo &source, const QString &path) {
        QVirtualFileInfo info;
        info.path = path;
        info.name = nameOf(path);
        info.displayName = info.name;
        info.isRoot = path == "/";
        if (source.attributes.testFlag(Sftp::Permissions)) {
            switch (source.mode & 0170000) {
                case 0040000:
                    info.type = QVirtualFileInfo::Directory;
                    break;
                case 0100000:
                    info.type = QVirtualFileInfo::File;
                    break;
                case 0120000:
                    info.type = QVirtualFileInfo::SymbolicLink;
                    break;
                case 0:
                    break;
                default:
                    info.type = QVirtualFileInfo::Other;
                    break;
            }
            info.permissions = source.permissions;
            info.attributes |= QVirtualFileInfo::Permissions;
        }
        if (source.attributes.testFlag(Sftp::Size)) {
            info.size = source.size;
            info.attributes |= QVirtualFileInfo::Size;
        }
        if (source.attributes.testFlag(Sftp::Times)) {
            info.lastModified = QDateTime::fromSecsSinceEpoch(source.modificationTime, Qt::UTC);
            info.attributes |= QVirtualFileInfo::ModifiedTime;
        }
        return info;
    }

    template <class Base> class SftpJob : public Base {
    protected:
        using Error = QVirtualJob::Error;

        SftpJob(Sftp *session, QObject *parent) : Base(parent), session(session) {
            if (!session) {
                return;
            }
            QObject::connect(this, &QVirtualJob::finished, this, [this] {
                if (this->session) {
                    QObject::disconnect(this->session, nullptr, this, nullptr);
                }
            });
            QObject::connect(session, &QObject::destroyed, this, [this] {
                this->finish(QVirtualJob::FileSystemClosed, QStringLiteral("SFTP session destroyed"));
            });
            QObject::connect(session, &Sftp::closed, this, [this] {
                this->finish(QVirtualJob::FileSystemClosed, QStringLiteral("SFTP session closed"));
            });
            QObject::connect(session, &Sftp::requestFailed, this,
                             [this](quint64 id, Sftp::Error error, const QString &message) {
                                 if (requestId && id == requestId) {
                                     this->finish(mapError(error), message, static_cast<int>(error));
                                 }
                             });
        }

        bool ready() {
            if (!session || session->state() == Sftp::Closed) {
                this->finish(QVirtualJob::FileSystemClosed, QStringLiteral("SFTP session closed"));
                return false;
            }
            if (session->state() != Sftp::Ready) {
                this->finish(QVirtualJob::NotReady, QStringLiteral("SFTP session is not ready"));
                return false;
            }
            return true;
        }

        QPointer<Sftp> session;
        quint64 requestId = 0;
    };

    class SftpStatJob final : public SftpJob<QVirtualStatJob> {
    public:
        SftpStatJob(Sftp *session, QString path, QObject *parent) : SftpJob(session, parent) {
            if (session) {
                connect(session, &Sftp::fileInfoAvailable, this, [this, path](quint64 id, const Sftp::FileInfo &info) {
                    if (!isFinished() && requestId && id == requestId) {
                        setInfo(snapshot(info, path));
                    }
                });
                connect(session, &Sftp::requestFinished, this, [this](quint64 id) {
                    if (requestId && id == requestId) {
                        finish();
                    }
                });
            }
            QTimer::singleShot(0, this, [this, path = std::move(path)] {
                if (isFinished()) {
                    return;
                }
                if (path.isNull()) {
                    finish(InvalidPath, QStringLiteral("Expected an absolute SFTP path"));
                    return;
                }
                if (!ready()) {
                    return;
                }
                requestId = this->session->stat(path, false);
                if (!requestId) {
                    finish(NotReady, QStringLiteral("SFTP request was not accepted"));
                }
            });
        }
    };

    class SftpListJob final : public SftpJob<QVirtualListJob> {
    public:
        SftpListJob(Sftp *session, QString path, QVirtualListOptions options, bool roots, QObject *parent)
            : SftpJob(session, parent) {
            if (session && !roots) {
                connect(session, &Sftp::directoryEntries, this,
                        [this, path, options](quint64 id, const QList<Sftp::FileInfo> &entries) {
                            if (isFinished() || !requestId || id != requestId) {
                                return;
                            }
                            QList<QVirtualFileInfo> batch;
                            const QPointer<SftpListJob> guard(this);
                            for (const auto &entry : entries) {
                                if (entry.name.isEmpty() || entry.name == "." || entry.name == ".." ||
                                    entry.name.contains('/') || entry.name.contains(QChar::Null) ||
                                    (!options.includeHidden && entry.name.startsWith('.'))) {
                                    continue;
                                }
                                batch.append(snapshot(
                                    entry, path + (path.endsWith('/') ? QString() : QStringLiteral("/")) + entry.name));
                                if (batch.size() == options.batchSize) {
                                    publish(batch);
                                    if (!guard || guard->isFinished()) {
                                        return;
                                    }
                                    batch.clear();
                                }
                            }
                            if (!batch.isEmpty()) {
                                publish(batch);
                            }
                        });
                connect(session, &Sftp::requestFinished, this, [this](quint64 id) {
                    if (requestId && id == requestId) {
                        finish();
                    }
                });
            }
            QTimer::singleShot(0, this, [this, path = std::move(path), options, roots] {
                if (isFinished()) {
                    return;
                }
                if (path.isNull()) {
                    finish(InvalidPath, QStringLiteral("Expected an absolute SFTP path"));
                    return;
                }
                if (options.batchSize < 1 || options.batchSize > 65536) {
                    finish(InvalidArgument, QStringLiteral("Expected batchSize in 1..65536"));
                    return;
                }
                if (!ready()) {
                    return;
                }
                if (roots) {
                    QVirtualFileInfo root;
                    root.path = root.name = root.displayName = QStringLiteral("/");
                    root.type = QVirtualFileInfo::Directory;
                    root.isRoot = true;
                    const QPointer<SftpListJob> guard(this);
                    publish({root});
                    if (guard) {
                        guard->finish();
                    }
                    return;
                }
                requestId = this->session->listDirectory(path);
                if (!requestId) {
                    finish(NotReady, QStringLiteral("SFTP request was not accepted"));
                }
            });
        }
    };

    /** 原生路径传输的 Qt 外观；块数据仍完全由 qssh 的 Asio/线程池流水线传递。 */
    class SftpCopyJob final : public QVirtualTransferJob {
    public:
        SftpCopyJob(Sftp *session, bool upload, QString local, QString remote, QVirtualTransferOptions options,
                    std::function<Sftp::Error(QSshSftpTransfer *)> remoteError, QObject *parent)
            : QVirtualTransferJob(parent),
              session(session),
              remoteError(std::move(remoteError)) {
            QTimer::singleShot(0, this, [this, upload, local = std::move(local), remote = std::move(remote), options] {
                if (isFinished()) {
                    return;
                }
                if (!this->session || this->session->state() == Sftp::Closed) {
                    finish(FileSystemClosed, QStringLiteral("SFTP session closed"));
                    return;
                }
                if (this->session->state() != Sftp::Ready) {
                    finish(NotReady, QStringLiteral("SFTP session is not ready"));
                    return;
                }
                if (options.bufferSize < 65536 || options.bufferSize > 64 * 1024 * 1024 || options.idleTimeoutMs <= 0 ||
                    (options.atomicReplace && !options.overwrite)) {
                    finish(InvalidArgument, QStringLiteral("Invalid transfer options"));
                    return;
                }
                if (upload && options.atomicReplace) {
                    finish(Unsupported, QStringLiteral("SFTP v3 cannot guarantee atomic replacement"));
                    return;
                }
                lease = reserveBuffer(options);
                if (!lease) {
                    finish(ResourceLimit, QStringLiteral("Global transfer buffer budget exhausted"));
                    return;
                }
                QSshSftpTransfer::Options nativeOptions;
                nativeOptions.overwrite = options.overwrite;
                nativeOptions.bufferSize = options.bufferSize;
                nativeOptions.idleTimeoutMs = options.idleTimeoutMs;
                native = upload ? this->session->upload(local, remote, nativeOptions)
                                : this->session->download(remote, local, nativeOptions);
                connect(native, &QSshSftpTransfer::progress, this,
                        [this](qint64 count, qint64 total) { report(count, total, phase()); });
                connect(native, &QSshSftpTransfer::stateChanged, this, [this](QSshSftpTransfer::State state) {
                    if (state == QSshSftpTransfer::Running) {
                        report(bytesTransferred(), totalBytes(), Transferring);
                    } else if (state == QSshSftpTransfer::Finishing) {
                        report(bytesTransferred(), totalBytes(), Committing);
                    }
                });
                connect(native, &QSshSftpTransfer::finished, this, [this] {
                    const auto state = native->state();
                    const auto nativeError = native->error();
                    auto message = native->errorString();
                    Error error = NoError;
                    if (state == QSshSftpTransfer::Cancelled) {
                        error = OutcomeUnknown;
                        message = QStringLiteral(
                            "Transfer stopped; destination or final commit may already have changed");
                    } else if (state != QSshSftpTransfer::Succeeded) {
                        error = nativeError == QSshSftpTransfer::TimeoutError ? OutcomeUnknown
                                : nativeError == QSshSftpTransfer::SessionClosed ||
                                        nativeError == QSshSftpTransfer::RemoteError
                                    ? OutcomeUnknown
                                : nativeError == QSshSftpTransfer::InvalidConfiguration ? InvalidArgument
                                                                                        : IoError;
                    }
                    const auto detailed = this->remoteError(native);
                    if (nativeError == QSshSftpTransfer::RemoteError && detailed >= Sftp::NoSuchFile &&
                        detailed <= Sftp::Unsupported && detailed != Sftp::NoConnection &&
                        detailed != Sftp::ConnectionLost) {
                        error = mapError(detailed);
                    }
                    const QPointer<SftpCopyJob> guard(this);
                    report(native->bytesTransferred(), native->totalBytes(), phase());
                    if (!guard) {
                        return;
                    }
                    lease.reset();
                    finish(error, message, static_cast<int>(detailed));
                });
                connect(native, &QObject::destroyed, this, [this] {
                    native = nullptr;
                    lease.reset();
                    finish(OutcomeUnknown, QStringLiteral("Native transfer destroyed before confirmation"));
                });
            });
        }

        ~SftpCopyJob() override {
            if (native) {
                disconnect(native, nullptr, this, nullptr);
                delete native.data();
            }
        }

    protected:
        void requestCancel() override {
            if (native) {
                native->cancel();
            } else {
                QVirtualJob::requestCancel();
            }
        }

    private:
        QPointer<Sftp> session;                                     ///< 借用的前端子系统，仅 Qt 线程访问。
        QPointer<QSshSftpTransfer> native;                          ///< 原生传输，包装器销毁时显式回收。
        std::shared_ptr<void> lease;                                ///< VFS 全局缓冲预算租约。
        std::function<Sftp::Error(QSshSftpTransfer *)> remoteError; ///< 只在 Qt 线程读取的具体原生错误。
    };
} // namespace

QSftpFileSystem::QSftpFileSystem(QSshSftpSession *session, QObject *parent)
    : QVirtualFileSystem(parent),
      session(session) {
    Q_ASSERT(!session || session->thread() == thread());
}

QSftpFileSystem::~QSftpFileSystem() = default;

QVirtualFileSystem::Capabilities QSftpFileSystem::capabilities() const {
    return Read | Write | RandomRead | RandomWrite | Append | Remove | Rename;
}

std::shared_ptr<qvfs::Backend> QSftpFileSystem::backend(const QString &path) const {
    return session ? qsshSftpBackend(session->d_func()->dispatcher, path)
                   : qvfs::rejectedBackend(QVirtualJob::FileSystemClosed, QStringLiteral("SFTP session destroyed"));
}

QVirtualTransferJob *QSftpFileSystem::tryCopy(QVirtualFile *source, QVirtualFile *target,
                                              const QVirtualTransferOptions &options, QObject *parent) {
    if (target->fileSystem() == this && qobject_cast<QLocalFileSystem *>(source->fileSystem())) {
        return new SftpCopyJob(
            session, true, source->path(), target->path(), options,
            [](QSshSftpTransfer *transfer) { return transfer->d_func()->frontRemoteError; }, parent);
    }
    if (source->fileSystem() == this && qobject_cast<QLocalFileSystem *>(target->fileSystem())) {
        return new SftpCopyJob(
            session, false, target->path(), source->path(), options,
            [](QSshSftpTransfer *transfer) { return transfer->d_func()->frontRemoteError; }, parent);
    }
    return nullptr;
}

QVirtualListJob *QSftpFileSystem::roots(QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new SftpListJob(session, QStringLiteral("/"), {}, true, parent ? parent : this);
}

QVirtualStatJob *QSftpFileSystem::stat(const QString &path, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new SftpStatJob(session, normalizePath(path), parent ? parent : this);
}

QVirtualListJob *QSftpFileSystem::list(const QString &path, const QVirtualListOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new SftpListJob(session, normalizePath(path), options, false, parent ? parent : this);
}

QString QSftpFileSystem::normalizePath(const QString &path) const {
    if (!path.startsWith('/') || path.contains(QChar::Null)) {
        return {};
    }
    QStringList parts;
    // 不使用本机 QDir 路径规则：Windows 客户端也必须保留远端文件名中的反斜杠。
    for (const auto &part : path.split('/', Qt::SkipEmptyParts)) {
        if (part == ".") {
            continue;
        }
        if (part == "..") {
            if (!parts.isEmpty()) {
                parts.removeLast();
            }
        } else {
            parts.append(part);
        }
    }
    return QStringLiteral("/") + parts.join('/');
}

QString QSftpFileSystem::parentPath(const QString &path) const {
    if (path == "/") {
        return {};
    }
    const auto separator = path.lastIndexOf('/');
    return separator == 0 ? QStringLiteral("/") : path.left(separator);
}

QString QSftpFileSystem::childPath(const QString &path, const QString &name) const {
    if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains(QChar::Null)) {
        return {};
    }
    return path + (path.endsWith('/') ? QString() : QStringLiteral("/")) + name;
}

QString QSftpFileSystem::fileName(const QString &path) const {
    return nameOf(path);
}
