#include "qsftpfilebackend_p.h"
#include "qsshsftppermissions_p.h"

#include <limits>

namespace {
    using Native = QSshSftpSessionPrivate;
    using Sftp = QSshSftpSession;
    using Job = QVirtualJob;
    using namespace qvfs;
    using Dispatcher = std::shared_ptr<Native::Dispatcher>;

    Result convert(const Native::Result &native, bool mutation) {
        Result result;
        result.data = native.data;
        result.eof = native.eof;
        result.message = native.message;
        result.nativeError = native.error;
        switch (native.error) {
            case Sftp::NoError:
                break;
            case Sftp::NoSuchFile:
                result.error = Job::NotFound;
                break;
            case Sftp::PermissionDenied:
                result.error = Job::PermissionDenied;
                break;
            case Sftp::Unsupported:
                result.error = Job::Unsupported;
                break;
            case Sftp::InvalidState:
                result.error = Job::NotReady;
                break;
            case Sftp::TimeoutError:
                result.error = mutation && native.dispatched ? Job::OutcomeUnknown : Job::Timeout;
                break;
            case Sftp::Cancelled:
                result.error = !native.dispatched ? Job::OperationCancelled
                               : mutation         ? Job::OutcomeUnknown
                                                  : Job::FileSystemClosed;
                break;
            case Sftp::NoConnection:
            case Sftp::ConnectionLost:
            case Sftp::SessionClosed:
            case Sftp::ProtocolError:
                result.error = mutation && native.dispatched ? Job::OutcomeUnknown : Job::FileSystemClosed;
                break;
            default:
                result.error = Job::IoError;
                break;
        }
        return result;
    }

    /** 在任意后台线程接受请求，所有协议和完成处理仍在原有 worker 上推进。 */
    void request(const Dispatcher &dispatcher, Native::Request req, ControlPtr control,
                 std::function<void(Native::Result)> cb) {
        if (!dispatcher || !dispatcher->enqueue([req = std::move(req), control, cb](Native *native) mutable {
                if (!native || native->terminated) {
                    Native::Result r;
                    r.error = Sftp::SessionClosed;
                    r.message = QStringLiteral("SFTP session closed");
                    cb(std::move(r));
                    return;
                }
                if (native->phase != Sftp::Ready) {
                    Native::Result r;
                    r.error = Sftp::InvalidState;
                    r.message = QStringLiteral("SFTP session is not ready");
                    cb(std::move(r));
                    return;
                }
                if (control) {
                    req.cancelled = [control] { return control->cancelled.load(); };
                }
                native->enqueueInternal(std::move(req), std::move(cb));
            })) {
            Native::Result r;
            r.error = Sftp::SessionClosed;
            r.message = QStringLiteral("SFTP session destroyed");
            cb(std::move(r));
        }
    }

    class SftpHandle final : public Handle, public std::enable_shared_from_this<SftpHandle> {
    public:
        SftpHandle(Dispatcher dispatcher, quint64 id, QVirtualOpenOptions options)
            : dispatcher(std::move(dispatcher)),
              id(id),
              options(options) {}

        ~SftpHandle() override {
            if (!closing.exchange(true)) {
                request(dispatcher, {.kind = Native::Close, .handle = id}, {}, [](Native::Result) {});
            }
        }

        bool randomWrite() const override {
            return !options.append;
        }

        void read(quint64 offset, quint32 size, ControlPtr control, Callback cb) override {
            if (closing || options.access == QVirtualOpenOptions::WriteOnly) {
                cb(failure(Job::InvalidArgument, QStringLiteral("Handle is not readable")));
                return;
            }
            if (pending.fetch_add(size) + size > 64 * 1024 * 1024) {
                pending -= size;
                cb(failure(Job::ResourceLimit, QStringLiteral("Handle read budget exhausted")));
                return;
            }
            request(dispatcher, {.kind = Native::Read, .handle = id, .offset = offset, .value = size},
                    std::move(control), [self = shared_from_this(), size, cb = std::move(cb)](Native::Result r) {
                        self->pending -= size;
                        cb(convert(r, false));
                    });
        }

        void write(quint64 offset, QByteArray data, ControlPtr control, Callback cb) override {
            if (closing || options.access == QVirtualOpenOptions::ReadOnly) {
                cb(failure(Job::InvalidArgument, QStringLiteral("Handle is not writable")));
                return;
            }
            const auto size = data.size();
            if (pending.fetch_add(size) + size > 64 * 1024 * 1024) {
                pending -= size;
                cb(failure(Job::ResourceLimit, QStringLiteral("Handle write budget exhausted")));
                return;
            }
            request(dispatcher, {.kind = Native::Write, .handle = id, .offset = offset, .data = std::move(data)},
                    std::move(control), [self = shared_from_this(), size, cb = std::move(cb)](Native::Result r) {
                        self->pending -= size;
                        auto result = convert(r, true);
                        if (result.error == Job::NoError) {
                            result.affected = size;
                        }
                        cb(std::move(result));
                    });
        }

        void close(bool, Callback cb) override {
            if (closing.exchange(true)) {
                cb(failure(Job::InvalidArgument, QStringLiteral("Handle is already closing")));
                return;
            }
            request(dispatcher, {.kind = Native::Close, .handle = id}, {},
                    [cb = std::move(cb)](Native::Result r) { cb(convert(r, true)); });
        }

    private:
        Dispatcher dispatcher;             ///< 安全后台投递入口，不拥有会话。
        const quint64 id;                  ///< 会话内的不透明文件句柄编号。
        const QVirtualOpenOptions options; ///< 权限和追加模式。
        std::atomic_bool closing = false;  ///< 关闭屏障。
        std::atomic<qint64> pending = 0;   ///< 待处理数据请求的有界预算。
    };

    /** 单请求链推进的递归删除，不使用 C++ 递归栈，不跟随链接，待遍历项有硬上限。 */
    struct Removal : std::enable_shared_from_this<Removal> {
        struct Entry {
            QString path;
            bool expanded = false;
        };

        Dispatcher dispatcher;
        ControlPtr control;
        QVirtualRemoveOptions options;
        Callback callback;
        std::vector<Entry> stack;
        Result result;

        void fail(Result error, const QString &path) {
            error.affected = result.affected;
            error.failedPath = path;
            if (error.affected && error.error != Job::OutcomeUnknown && error.error != Job::OperationCancelled) {
                error.error = Job::PartialFailure;
            }
            callback(std::move(error));
        }

        void next() {
            if (control->cancelled) {
                fail(failure(Job::OperationCancelled, QStringLiteral("Deletion cancelled")),
                     stack.empty() ? QString() : stack.back().path);
                return;
            }
            if (stack.empty()) {
                callback(result);
                return;
            }
            auto entry = std::move(stack.back());
            stack.pop_back();
            request(dispatcher, {.kind = Native::Lstat, .path = entry.path}, control,
                    [self = shared_from_this(), entry](Native::Result info) {
                        if (info.error == Sftp::NoSuchFile && self->options.ignoreMissing) {
                            self->next();
                            return;
                        }
                        if (info.error != Sftp::NoError) {
                            self->fail(convert(info, false), entry.path);
                            return;
                        }
                        if (!info.info.attributes.testFlag(Sftp::Permissions)) {
                            self->fail(failure(Job::Unsupported, QStringLiteral("Server did not report entry type")),
                                       entry.path);
                            return;
                        }
                        const bool dir = (info.info.mode & 0170000) == 0040000;
                        if (dir && self->options.recursive && !entry.expanded) {
                            auto children = std::make_shared<std::vector<Entry>>();
                            auto exhausted = std::make_shared<bool>(false);
                            Native::Request list{.kind = Native::List, .path = entry.path};
                            list.entries = [self, children, exhausted,
                                            path = entry.path](const QList<Sftp::FileInfo> &batch) {
                                if (self->control->cancelled) {
                                    return false;
                                }
                                for (const auto &info : batch) {
                                    if (info.name.isEmpty() || info.name == "." || info.name == ".." ||
                                        info.name.contains('/') || info.name.contains(QChar::Null)) {
                                        continue;
                                    }
                                    if (self->stack.size() + children->size() >= 65535) {
                                        *exhausted = true;
                                        return false;
                                    }
                                    children->push_back({path + '/' + info.name});
                                }
                                return true;
                            };
                            request(self->dispatcher, std::move(list), self->control,
                                    [self, entry, children, exhausted](Native::Result r) {
                                        if (*exhausted) {
                                            self->fail(failure(Job::ResourceLimit,
                                                               QStringLiteral("Deletion traversal budget exhausted")),
                                                       entry.path);
                                            return;
                                        }
                                        if (r.error != Sftp::NoError) {
                                            self->fail(convert(r, false), entry.path);
                                            return;
                                        }
                                        self->stack.push_back({entry.path, true});
                                        for (auto &child : *children) {
                                            self->stack.push_back(std::move(child));
                                        }
                                        self->next();
                                    });
                            return;
                        }
                        request(self->dispatcher, {.kind = dir ? Native::Rmdir : Native::Remove, .path = entry.path},
                                self->control, [self, path = entry.path](Native::Result r) {
                                    if (r.error == Sftp::NoError) {
                                        ++self->result.affected;
                                    } else if (r.error != Sftp::NoSuchFile || !self->options.ignoreMissing) {
                                        self->fail(convert(r, true), path);
                                        return;
                                    }
                                    self->next();
                                });
                    });
        }
    };

    class SftpBackend final : public Backend {
    public:
        SftpBackend(Dispatcher dispatcher, QString path)
            : dispatcher(std::move(dispatcher)),
              location(std::move(path)) {}

        const void *identity() const override {
            return dispatcher.get();
        }

        QString path() const override {
            return location;
        }

        void open(QVirtualOpenOptions options, ControlPtr control, Callback cb) override {
            if (options.atomicReplace) {
                cb(failure(Job::Unsupported, QStringLiteral("SFTP v3 cannot guarantee atomic replacement")));
                return;
            }
            if (location == "/") {
                cb(failure(Job::InvalidArgument, QStringLiteral("Cannot open root as a file")));
                return;
            }
            quint32 flags = options.access == QVirtualOpenOptions::ReadOnly    ? Sftp::Read
                            : options.access == QVirtualOpenOptions::WriteOnly ? Sftp::Write
                                                                               : Sftp::Read | Sftp::Write;
            if (options.creation == QVirtualOpenOptions::CreateNew) {
                flags |= Sftp::Create | Sftp::Exclusive;
            }
            if (options.creation == QVirtualOpenOptions::OpenOrCreate) {
                flags |= Sftp::Create;
            }
            if (options.creation == QVirtualOpenOptions::Replace) {
                flags |= Sftp::Create | Sftp::Truncate;
            }
            if (options.append) {
                flags |= Sftp::Append;
            }
            // STAT 只用于大小和类型快照；是否排他创建仍由 OPEN 的 Exclusive 保证。
            request(dispatcher, {.kind = Native::Stat, .path = location}, control,
                    [dispatcher = dispatcher, path = location, options, flags, control,
                     cb = std::move(cb)](Native::Result stat) {
                        if (stat.error != Sftp::NoError && !(stat.error == Sftp::NoSuchFile &&
                                                             options.creation != QVirtualOpenOptions::OpenExisting)) {
                            cb(convert(stat, false));
                            return;
                        }
                        if (stat.error == Sftp::NoError && options.creation == QVirtualOpenOptions::CreateNew) {
                            cb(failure(Job::AlreadyExists, QStringLiteral("Destination already exists")));
                            return;
                        }
                        if (stat.error == Sftp::NoError && stat.info.attributes.testFlag(Sftp::Permissions) &&
                            (stat.info.mode & 0170000) == 0040000) {
                            cb(failure(Job::InvalidArgument, QStringLiteral("Cannot open a directory as a file")));
                            return;
                        }
                        qint64 size = -1;
                        if (stat.error == Sftp::NoError && stat.info.attributes.testFlag(Sftp::Size)) {
                            if (stat.info.size > quint64(std::numeric_limits<qint64>::max())) {
                                cb(failure(Job::InvalidArgument,
                                           QStringLiteral("File size exceeds supported transfer range")));
                                return;
                            }
                            size = static_cast<qint64>(stat.info.size);
                        }
                        request(dispatcher,
                                {.kind = Native::Open,
                                 .path = path,
                                 .value = flags,
                                 .mode = qssh_sftp::permissionsToMode(options.permissions)},
                                control, [dispatcher, options, flags, size, cb = std::move(cb)](Native::Result r) {
                                    auto result = convert(r, flags & (Sftp::Create | Sftp::Truncate));
                                    if (result.error == Job::NoError) {
                                        result.handle = std::make_shared<SftpHandle>(dispatcher, r.handle, options);
                                        result.size = options.creation == QVirtualOpenOptions::Replace ||
                                                              options.creation == QVirtualOpenOptions::CreateNew
                                                          ? 0
                                                          : size;
                                    }
                                    cb(std::move(result));
                                });
                    });
        }

        void remove(QVirtualRemoveOptions options, ControlPtr control, Callback cb) override {
            if (location == "/") {
                cb(failure(Job::InvalidArgument, QStringLiteral("Cannot remove a root")));
                return;
            }
            auto state = std::make_shared<Removal>();
            state->dispatcher = dispatcher;
            state->control = std::move(control);
            state->options = options;
            state->callback = std::move(cb);
            state->stack.push_back({location});
            state->next();
        }

        void rename(QString target, QVirtualRenameOptions options, ControlPtr control, Callback cb) override {
            if (location == "/" || target == "/") {
                cb(failure(Job::InvalidArgument, QStringLiteral("Cannot rename a root")));
                return;
            }
            if (options.overwrite || options.requireAtomic) {
                cb(failure(Job::Unsupported, QStringLiteral("SFTP v3 does not guarantee requested rename policy")));
                return;
            }
            request(dispatcher, {.kind = Native::Rename, .path = location, .secondPath = std::move(target)},
                    std::move(control), [cb = std::move(cb)](Native::Result r) {
                        auto result = convert(r, true);
                        if (result.error == Job::NoError) {
                            result.affected = 1;
                        }
                        cb(std::move(result));
                    });
        }

    private:
        Dispatcher dispatcher;  ///< 安全分发器，不延长原生会话生命周期。
        const QString location; ///< 规范化的远端绝对路径。
    };
} // namespace

std::shared_ptr<qvfs::Backend> qsshSftpBackend(std::shared_ptr<Native::Dispatcher> dispatcher, QString path) {
    return std::make_shared<SftpBackend>(std::move(dispatcher), std::move(path));
}
