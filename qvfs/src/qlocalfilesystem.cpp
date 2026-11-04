#include <QDir>
#include <QDirIterator>
#include <QFileInfo>
#include <QPointer>
#include <QThreadPool>
#include <qvfs/private/qvirtualbackend_p.h>
#include <qvfs/qlocalfilesystem.h>

namespace {
    QString rootOf(const QString &path) {
#ifdef Q_OS_WIN
        if (path.startsWith(QStringLiteral("//"))) {
            const auto serverEnd = path.indexOf('/', 2);
            const auto shareEnd = path.indexOf('/', serverEnd + 1);
            return shareEnd < 0 ? path : path.left(shareEnd);
        }
        return path.left(3);
#else
        Q_UNUSED(path)
        return QStringLiteral("/");
#endif
    }

    QString baseName(const QString &path) {
        return path == rootOf(path) ? path : path.mid(path.lastIndexOf('/') + 1);
    }

    QVirtualFileInfo snapshot(const QFileInfo &source, const QString &path) {
        QVirtualFileInfo info;
        info.path = path;
        info.name = baseName(path);
        info.displayName = info.name;
        info.isRoot = path == rootOf(path);
        // QFileInfo 的大小、时间和权限会跟随符号链接；不将目标属性误报为链接属性。
        if (source.isSymLink()) {
            info.type = QVirtualFileInfo::SymbolicLink;
            return info;
        }
        info.type = source.isDir()    ? QVirtualFileInfo::Directory
                    : source.isFile() ? QVirtualFileInfo::File
                                      : QVirtualFileInfo::Other;
        info.size = static_cast<quint64>(qMax<qint64>(0, source.size()));
        info.lastModified = source.lastModified();
        info.permissions = source.permissions();
        info.attributes = QVirtualFileInfo::Size | QVirtualFileInfo::Permissions;
        if (info.lastModified.isValid()) {
            info.attributes |= QVirtualFileInfo::ModifiedTime;
        }
        return info;
    }

    class LocalStatJob final : public QVirtualStatJob {
    public:
        LocalStatJob(QString path, QObject *parent) : QVirtualStatJob(parent) {
            const auto delivery = context();
            QThreadPool::globalInstance()->start(QRunnable::create([delivery, path = std::move(path)] {
                if (isStopped(delivery)) {
                    return;
                }
                if (path.isNull()) {
                    deliver(delivery, [](QVirtualJob *job) {
                        dynamic_cast<LocalStatJob *>(job)->finish(InvalidPath,
                                                                  QStringLiteral("Expected an absolute local path"));
                    });
                    return;
                }
                const QFileInfo source(path);
                if (!source.exists() && !source.isSymLink()) {
                    deliver(delivery, [](QVirtualJob *job) {
                        dynamic_cast<LocalStatJob *>(job)->finish(
                            NotFound, QStringLiteral("Path does not exist or cannot be accessed"));
                    });
                    return;
                }
                const auto info = snapshot(source, path);
                deliver(delivery, [info](QVirtualJob *job) {
                    auto *stat = dynamic_cast<LocalStatJob *>(job);
                    stat->setInfo(info);
                    stat->finish();
                });
            }));
        }
    };

    class LocalListJob final : public QVirtualListJob {
    public:
        LocalListJob(QList<QVirtualFileInfo> roots, QObject *parent) : QVirtualListJob(parent) {
            deliver(context(), [roots = std::move(roots)](QVirtualJob *job) {
                auto *list = dynamic_cast<LocalListJob *>(job);
                const QPointer guard(list);
                list->publish(roots);
                if (guard) {
                    guard->finish();
                }
            });
        }

        LocalListJob(QString path, QVirtualListOptions options, QObject *parent) : QVirtualListJob(parent) {
            const auto delivery = context();
            QThreadPool::globalInstance()->start(QRunnable::create([delivery, path = std::move(path), options] {
                if (isStopped(delivery)) {
                    return;
                }
                const auto fail = [&](Error error, const QString &message) {
                    deliver(delivery, [error, message](QVirtualJob *job) {
                        dynamic_cast<LocalListJob *>(job)->finish(error, message);
                    });
                };
                if (path.isNull()) {
                    fail(InvalidPath, QStringLiteral("Expected an absolute local path"));
                    return;
                }
                if (options.batchSize < 1 || options.batchSize > 65536) {
                    fail(InvalidArgument, QStringLiteral("Expected batchSize in 1..65536"));
                    return;
                }
                const QFileInfo directory(path);
                if (!directory.exists()) {
                    fail(NotFound, QStringLiteral("Directory does not exist or cannot be accessed"));
                    return;
                }
                if (!directory.isDir()) {
                    fail(NotDirectory, QStringLiteral("Path is not a directory"));
                    return;
                }
                if (!directory.isReadable()) {
                    fail(PermissionDenied, QStringLiteral("Directory is not readable"));
                    return;
                }
                auto filters = QDir::Filters(QDir::AllEntries | QDir::NoDotAndDotDot | QDir::System);
                if (options.includeHidden) {
                    filters |= QDir::Hidden;
                }
                QDirIterator iterator(path, filters, QDirIterator::NoIteratorFlags);
                QList<QVirtualFileInfo> batch;
                const auto flush = [&] {
                    auto entries = std::move(batch);
                    batch.clear();
                    // 每个遍历最多交付一批尚未消费的数据，避免慢 GUI 导致无界排队。
                    return deliver(
                        delivery,
                        [entries = std::move(entries)](QVirtualJob *job) {
                            dynamic_cast<LocalListJob *>(job)->publish(entries);
                        },
                        true);
                };
                while (!isStopped(delivery) && iterator.hasNext()) {
                    iterator.next();
                    batch.append(snapshot(iterator.fileInfo(), QDir::fromNativeSeparators(iterator.filePath())));
                    if (batch.size() >= options.batchSize && !flush()) {
                        return;
                    }
                }
                if (isStopped(delivery) || (!batch.isEmpty() && !flush())) {
                    return;
                }
                deliver(delivery, [](QVirtualJob *job) { dynamic_cast<LocalListJob *>(job)->finish(); });
            }));
        }
    };
} // namespace

QLocalFileSystem::QLocalFileSystem(QObject *parent) : QVirtualFileSystem(parent) {}

QVirtualFileSystem::Capabilities QLocalFileSystem::capabilities() const {
    Capabilities flags = Read | Write | RandomRead | RandomWrite | Append | Remove | Rename | AtomicReplace;
#ifndef Q_OS_WIN
    flags |= AtomicRename;
#endif
    return flags;
}

std::shared_ptr<qvfs::Backend> QLocalFileSystem::backend(const QString &path) const {
    return qvfs::localBackend(path, path == rootOf(path));
}

QVirtualListJob *QLocalFileSystem::roots(QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    QList<QVirtualFileInfo> roots;
    // 只取根路径，不查询卷标签、容量或根目录属性。
    for (const auto &drive : QDir::drives()) {
        QVirtualFileInfo info;
        info.path = normalizePath(drive.filePath());
        info.name = info.path;
        info.displayName = info.name;
        info.type = QVirtualFileInfo::Directory;
        info.isRoot = true;
        roots.append(info);
    }
    return new LocalListJob(std::move(roots), parent ? parent : this);
}

QVirtualStatJob *QLocalFileSystem::stat(const QString &path, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new LocalStatJob(normalizePath(path), parent ? parent : this);
}

QVirtualListJob *QLocalFileSystem::list(const QString &path, const QVirtualListOptions &options, QObject *parent) {
    Q_ASSERT(thread() == QThread::currentThread());
    return new LocalListJob(normalizePath(path), options, parent ? parent : this);
}

QString QLocalFileSystem::normalizePath(const QString &path) const {
    if (path.isEmpty() || path.contains(QChar::Null)) {
        return {};
    }
    const auto native = QDir::fromNativeSeparators(path);
    QString root;
    QString rest;
#ifdef Q_OS_WIN
    if (native.size() >= 3 && native.at(0).toUpper() >= QChar('A') && native.at(0).toUpper() <= QChar('Z') &&
        native.at(1) == ':' && native.at(2) == '/') {
        root = native.left(3);
        root[0] = root.at(0).toUpper();
        rest = native.mid(3);
    } else if (native.startsWith(QStringLiteral("//"))) {
        const auto parts = native.mid(2).split('/', Qt::SkipEmptyParts);
        if (parts.size() < 2 || parts[0] == "." || parts[0] == ".." || parts[0] == "?" || parts[1] == "." ||
            parts[1] == "..") {
            return {};
        }
        root = QStringLiteral("//") + parts[0] + '/' + parts[1];
        rest = parts.mid(2).join('/');
    } else {
        return {};
    }
#else
    if (!native.startsWith('/')) {
        return {};
    }
    root = QStringLiteral("/");
    rest = native.mid(1);
#endif
    QStringList parts;
    for (const auto &part : rest.split('/', Qt::SkipEmptyParts)) {
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
    return parts.isEmpty() ? root : root + (root.endsWith('/') ? QString() : QStringLiteral("/")) + parts.join('/');
}

QString QLocalFileSystem::parentPath(const QString &path) const {
    const auto root = rootOf(path);
    if (path == root) {
        return {};
    }
    const auto separator = path.lastIndexOf('/');
    return separator < root.size() ? root : path.left(separator);
}

QString QLocalFileSystem::childPath(const QString &path, const QString &name) const {
    if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains(QChar::Null)) {
        return {};
    }
#ifdef Q_OS_WIN
    if (name.contains('\\') || name.contains(':')) {
        return {};
    }
#endif
    return normalizePath(path + (path.endsWith('/') ? QString() : QStringLiteral("/")) + name);
}

QString QLocalFileSystem::fileName(const QString &path) const {
    return baseName(path);
}
