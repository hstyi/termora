#include <QDir>
#include <QDirIterator>
#include <QFile>
#include <QFileInfo>
#include <QSaveFile>
#include <cerrno>
#include <fcntl.h>
#include <limits>
#include <qvfs/private/qvirtualbackend_p.h>
#include <sys/stat.h>
#include <vector>
#ifdef Q_OS_WIN
#include <io.h>
#include <qt_windows.h>
#include <sys/stat.h>
#else
#include <unistd.h>
#ifdef Q_OS_LINUX
#include <sys/syscall.h>
#endif
#endif

namespace qvfs {
    namespace {
        using Job = QVirtualJob;

        Result systemError(const int code, const QString &path) {
            const auto type = code == EEXIST                      ? Job::AlreadyExists
                              : code == ENOENT                    ? Job::NotFound
                              : code == EACCES || code == EPERM   ? Job::PermissionDenied
                              : code == ENOTEMPTY                 ? Job::NotEmpty
                              : code == EXDEV                     ? Job::CrossFileSystem
                              : code == ENOTDIR                   ? Job::NotDirectory
                              : code == ENOSYS || code == ENOTSUP ? Job::Unsupported
                                                                  : Job::IoError;
            auto result = failure(type, QString::fromLocal8Bit(std::strerror(code)));
            result.nativeError = code;
            result.failedPath = path;
            return result;
        }

        Result deviceError(QFileDevice *file) {
            auto result = failure(file->error() == QFileDevice::PermissionsError ? Job::PermissionDenied : Job::IoError,
                                  file->errorString());
            result.nativeError = static_cast<int>(file->error());
            return result;
        }

        unsigned permissions(const QFileDevice::Permissions p) {
            unsigned mode = 0;
            if (p & (QFileDevice::ReadOwner | QFileDevice::ReadUser)) {
                mode |= 0400;
            }
            if (p & (QFileDevice::WriteOwner | QFileDevice::WriteUser)) {
                mode |= 0200;
            }
            if (p & (QFileDevice::ExeOwner | QFileDevice::ExeUser)) {
                mode |= 0100;
            }
            if (p & QFileDevice::ReadGroup) {
                mode |= 0040;
            }
            if (p & QFileDevice::WriteGroup) {
                mode |= 0020;
            }
            if (p & QFileDevice::ExeGroup) {
                mode |= 0010;
            }
            if (p & QFileDevice::ReadOther) {
                mode |= 0004;
            }
            if (p & QFileDevice::WriteOther) {
                mode |= 0002;
            }
            if (p & QFileDevice::ExeOther) {
                mode |= 0001;
            }
            return mode;
        }

        /** 同一后台句柄的 QFile 仅由队列访问；不让 QObject 所属线程参与实际 I/O。 */
        class LocalHandle final : public Handle, public std::enable_shared_from_this<LocalHandle> {
        public:
            std::shared_ptr<SerialQueue> queue = std::make_shared<SerialQueue>(); ///< 单句柄顺序队列。
            std::unique_ptr<QFileDevice> file; ///< 在线程池创建并移除线程归属的设备。
            QVirtualOpenOptions options;       ///< 打开策略快照。
            Result poison;                     ///< 记住写入失败，禁止提交不完整的原子替换。
            std::atomic_bool closing = false;  ///< 跨线程关闭入口屏障。
            std::atomic<qint64> pending = 0;   ///< 后端待处理块预算，最大 64 MiB。

            bool randomWrite() const override {
                return !options.append;
            }

            ~LocalHandle() override {
                if (file) {
                    auto abandoned = std::shared_ptr<QFileDevice>(file.release());
                    queue->post([abandoned] {});
                }
            }

            void read(quint64 offset, quint32 count, ControlPtr control, Callback cb) override {
                if (pending.fetch_add(count) + count > 64 * 1024 * 1024) {
                    pending -= count;
                    cb(failure(Job::ResourceLimit, QStringLiteral("Handle read window exhausted")));
                    return;
                }
                queue->post([self = shared_from_this(), offset, count, control, cb = std::move(cb)] {
                    Result result;
                    if (control->cancelled) {
                        result = failure(Job::OperationCancelled, QStringLiteral("Read cancelled"));
                    } else if (!self->file || self->options.access == QVirtualOpenOptions::WriteOnly ||
                               offset > static_cast<quint64>(std::numeric_limits<qint64>::max())) {
                        result = failure(Job::InvalidArgument,
                                         QStringLiteral("Handle is not readable or offset is too large"));
                    } else if (!self->file->seek(static_cast<qint64>(offset))) {
                        result = deviceError(self->file.get());
                    } else {
                        result.data.resize(static_cast<int>(count));
                        const auto n = self->file->read(result.data.data(), count);
                        if (n < 0) {
                            result = deviceError(self->file.get());
                        } else {
                            result.data.resize(static_cast<int>(n));
                            result.eof = n == 0;
                        }
                    }
                    self->pending -= count;
                    cb(std::move(result));
                });
            }

            void write(quint64 offset, QByteArray data, ControlPtr control, Callback cb) override {
                if (const auto cost = data.size(); pending.fetch_add(cost) + cost > 64 * 1024 * 1024) {
                    pending -= cost;
                    cb(failure(Job::ResourceLimit, QStringLiteral("Handle write window exhausted")));
                    return;
                }
                queue->post([self = shared_from_this(), offset, data = std::move(data), control, cb = std::move(cb)] {
                    Result result;
                    if (control->cancelled) {
                        result = failure(Job::OperationCancelled, QStringLiteral("Write cancelled before dispatch"));
                    } else if (!self->file || self->options.access == QVirtualOpenOptions::ReadOnly ||
                               offset > static_cast<quint64>(std::numeric_limits<qint64>::max())) {
                        result = failure(Job::InvalidArgument,
                                         QStringLiteral("Handle is not writable or offset is too large"));
                    } else if (self->poison.error != Job::NoError) {
                        result = self->poison;
                    } else if (!self->options.append && !self->file->seek(static_cast<qint64>(offset))) {
                        result = deviceError(self->file.get());
                    } else {
                        qint64 written = 0;
                        while (written < data.size()) {
                            const auto n = self->file->write(data.constData() + written, data.size() - written);
                            if (n <= 0) {
                                result = deviceError(self->file.get());
                                break;
                            }
                            written += n;
                        }
                        result.affected = written;
                    }
                    if (result.error != Job::NoError) {
                        self->poison = result;
                    }
                    self->pending -= data.size();
                    cb(std::move(result));
                });
            }

            void close(bool commit, Callback cb) override {
                if (closing.exchange(true)) {
                    cb(failure(Job::InvalidArgument, QStringLiteral("Handle already closed")));
                    return;
                }
                queue->post([self = shared_from_this(), commit, cb = std::move(cb)] {
                    Result result = self->poison;
                    if (self->file && commit && result.error == Job::NoError) {
                        if (self->options.atomicReplace) {
                            if (!dynamic_cast<QSaveFile *>(self->file.get())->commit()) {
                                result = deviceError(self->file.get());
                            }
                        } else if (self->options.access != QVirtualOpenOptions::ReadOnly && !self->file->flush()) {
                            result = deviceError(self->file.get());
                        }
                    }
                    self->file.reset();
                    cb(std::move(result));
                });
            }
        };

        Result eraseTree(const QString &path, const bool recursive, bool ignoreMissing, const ControlPtr &control) {
            struct Entry {
                QString path;
                bool expanded = false;
            };

            std::vector<Entry> stack{{path}};
            Result result;
            while (!stack.empty()) {
                auto entry = std::move(stack.back());
                stack.pop_back();
                if (control->cancelled) {
                    result.error = Job::OperationCancelled;
                    result.message = QStringLiteral("Deletion cancelled");
                    break;
                }
                QFileInfo info(entry.path);
                if (!info.exists() && !info.isSymLink()) {
                    if (ignoreMissing) {
                        continue;
                    }
                    result.error = result.affected ? Job::PartialFailure : Job::NotFound;
                    result.message = QStringLiteral("Path does not exist or cannot be accessed");
                    result.failedPath = entry.path;
                    break;
                }
                const bool directory = info.isDir() && !info.isSymLink();
                if (directory && recursive && !entry.expanded) {
                    stack.push_back({.path = entry.path, .expanded = true});
                    QDirIterator iterator(entry.path,
                                          QDir::AllEntries | QDir::Hidden | QDir::System | QDir::NoDotAndDotDot);
                    while (!control->cancelled && iterator.hasNext()) {
                        stack.push_back({iterator.next()});
                        if (stack.size() > 65536) {
                            result.error = Job::ResourceLimit;
                            result.message = QStringLiteral("Deletion traversal budget exhausted");
                            result.failedPath = entry.path;
                            return result;
                        }
                    }
                    continue;
                }
                if (directory ? QDir().rmdir(entry.path) : QFile::remove(entry.path)) {
                    ++result.affected;
                    continue;
                }
                result.error = result.affected ? Job::PartialFailure
                               : directory && !QDir(entry.path)
                                                   .isEmpty(QDir::AllEntries | QDir::NoDotAndDotDot | QDir::Hidden |
                                                            QDir::System)
                                   ? Job::NotEmpty
                                   : Job::IoError;
                result.message = QStringLiteral("Could not remove path");
                result.failedPath = entry.path;
                break;
            }
            return result;
        }

        class LocalBackend final : public Backend {
        public:
            LocalBackend(QString path, bool root) : location(std::move(path)), root(root) {}

            const void *identity() const override {
                static const int local = 0;
                return &local;
            }

            QString path() const override {
                return location;
            }

            void verifyCopyTarget(std::shared_ptr<Backend> target, ControlPtr control, Callback cb) override {
                auto queue = std::make_shared<SerialQueue>();
                queue->post([source = location, same = identity() == target->identity(), target = target->path(),
                             control, cb = std::move(cb)] {
                    if (control->cancelled) {
                        cb(failure(Job::OperationCancelled, QStringLiteral("Copy cancelled")));
                        return;
                    }
                    bool alias = false;
                    if (same) {
#ifdef Q_OS_WIN
                        const auto src = QFileInfo(source).canonicalFilePath(),
                                   dst = QFileInfo(target).canonicalFilePath();
                        alias = !src.isEmpty() && src.compare(dst, Qt::CaseInsensitive) == 0;
#else
                        struct stat src{}, dst{};
                        alias = ::stat(QFile::encodeName(source).constData(), &src) == 0 &&
                                ::stat(QFile::encodeName(target).constData(), &dst) == 0 && src.st_dev == dst.st_dev &&
                                src.st_ino == dst.st_ino;
#endif
                    }
                    cb(alias ? failure(Job::InvalidArgument,
                                       QStringLiteral("Source and destination refer to the same file"))
                             : Result());
                });
            }

            void open(QVirtualOpenOptions options, ControlPtr control, Callback cb) override {
                auto handle = std::make_shared<LocalHandle>();
                handle->options = options;
                handle->queue->post([handle, path = location, options, control, cb = std::move(cb)] {
                    if (control->cancelled) {
                        cb(failure(Job::OperationCancelled, QStringLiteral("Open cancelled")));
                        return;
                    }
                    if (QFileInfo(path).isDir()) {
                        cb(failure(Job::InvalidArgument, QStringLiteral("Cannot open a directory as a file")));
                        return;
                    }
                    const auto mode = options.access == QVirtualOpenOptions::ReadOnly    ? QIODevice::ReadOnly
                                      : options.access == QVirtualOpenOptions::WriteOnly ? QIODevice::WriteOnly
                                                                                         : QIODevice::ReadWrite;
                    if (options.atomicReplace) {
                        handle->file = std::make_unique<QSaveFile>(path);
                        handle->file->moveToThread(nullptr);
                        if (!handle->file->open(mode)) {
                            cb(deviceError(handle->file.get()));
                            return;
                        }
                    } else {
                        int flags = options.access == QVirtualOpenOptions::ReadOnly    ? O_RDONLY
                                    : options.access == QVirtualOpenOptions::WriteOnly ? O_WRONLY
                                                                                       : O_RDWR;
                        if (options.creation == QVirtualOpenOptions::CreateNew) {
                            flags |= O_CREAT | O_EXCL;
                        }
                        if (options.creation == QVirtualOpenOptions::OpenOrCreate) {
                            flags |= O_CREAT;
                        }
                        if (options.creation == QVirtualOpenOptions::Replace) {
                            flags |= O_CREAT | O_TRUNC;
                        }
                        if (options.append) {
                            flags |= O_APPEND;
                        }
#ifdef Q_OS_WIN
                        const int fd = _wopen(reinterpret_cast<const wchar_t *>(path.utf16()), flags | O_BINARY,
                                              _S_IREAD | _S_IWRITE);
#else
                        const int fd = ::open(QFile::encodeName(path).constData(), flags | O_CLOEXEC,
                                              permissions(options.permissions));
#endif
                        if (fd < 0) {
                            cb(systemError(errno, path));
                            return;
                        }
                        auto file = std::make_unique<QFile>();
                        file->moveToThread(nullptr);
                        if (!file->open(fd, mode | (options.append ? QIODevice::Append : QIODevice::NotOpen),
                                        QFileDevice::AutoCloseHandle)) {
                            auto result = deviceError(file.get());
#ifdef Q_OS_WIN
                            _close(fd);
#else
                            ::close(fd);
#endif
                            cb(std::move(result));
                            return;
                        }
                        handle->file = std::move(file);
                    }
                    Result result;
                    result.size = handle->file->size();
                    result.handle = handle;
                    cb(std::move(result));
                });
            }

            void remove(QVirtualRemoveOptions options, ControlPtr control, Callback cb) override {
                auto queue = std::make_shared<SerialQueue>();
                queue->post([path = location, root = root, options, control, cb = std::move(cb)] {
                    if (root) {
                        cb(failure(Job::InvalidArgument, QStringLiteral("Cannot remove a root")));
                        return;
                    }
                    cb(eraseTree(path, options.recursive, options.ignoreMissing, control));
                });
            }

            void rename(QString target, QVirtualRenameOptions options, ControlPtr control, Callback cb) override {
                auto queue = std::make_shared<SerialQueue>();
                queue->post(
                    [source = location, target = std::move(target), root = root, options, control, cb = std::move(cb)] {
                        if (root) {
                            cb(failure(Job::InvalidArgument, QStringLiteral("Cannot rename a root")));
                            return;
                        }
                        if (control->cancelled) {
                            cb(failure(Job::OperationCancelled, QStringLiteral("Rename cancelled before dispatch")));
                            return;
                        }
                        int status = -1;
#ifdef Q_OS_WIN
                        if (options.requireAtomic) {
                            cb(failure(Job::Unsupported,
                                       QStringLiteral("Atomic rename is not guaranteed on this backend")));
                            return;
                        }
                        if (MoveFileExW(reinterpret_cast<const wchar_t *>(source.utf16()),
                                        reinterpret_cast<const wchar_t *>(target.utf16()),
                                        options.overwrite ? MOVEFILE_REPLACE_EXISTING : 0))
                            status = 0;
                        else {
                            const auto code = GetLastError();
                            auto result = failure(code == ERROR_ALREADY_EXISTS || code == ERROR_FILE_EXISTS
                                                      ? Job::AlreadyExists
                                                  : code == ERROR_NOT_SAME_DEVICE ? Job::CrossFileSystem
                                                                                  : Job::IoError,
                                                  QStringLiteral("Windows rename failed (%1)").arg(code));
                            result.nativeError = static_cast<int>(code);
                            cb(std::move(result));
                            return;
                        }
#elif defined(Q_OS_MACOS)
                        status = ::renamex_np(QFile::encodeName(source).constData(),
                                              QFile::encodeName(target).constData(),
                                              options.overwrite ? 0 : RENAME_EXCL);
#elif defined(Q_OS_LINUX) && defined(SYS_renameat2)
                        status = static_cast<int>(
                            ::syscall(SYS_renameat2, AT_FDCWD, QFile::encodeName(source).constData(), AT_FDCWD,
                                      QFile::encodeName(target).constData(), options.overwrite ? 0 : 1));
#else
                        if (!options.overwrite) {
                            cb(failure(Job::Unsupported,
                                       QStringLiteral("Exclusive rename is not supported on this platform")));
                            return;
                        }
                        status = ::rename(QFile::encodeName(source).constData(), QFile::encodeName(target).constData());
#endif
                        if (status != 0) {
                            cb(systemError(errno, source));
                            return;
                        }
                        Result result;
                        result.affected = 1;
                        cb(std::move(result));
                    });
            }

        private:
            const QString location; ///< 规范化路径。
            const bool root;        ///< 根入口不可修改。
        };
    } // namespace

    std::shared_ptr<Backend> localBackend(QString path, bool root) {
        return std::make_shared<LocalBackend>(std::move(path), root);
    }
} // namespace qvfs
