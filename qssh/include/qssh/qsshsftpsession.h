#pragma once

#include <QByteArray>
#include <QFileDevice>
#include <QList>
#include <QObject>
#include <QScopedPointer>
#include <qssh/qsshsftptransfer.h>

class QSshSession;
class QSshSftpSessionPrivate;

/**
 * 一条 SSH 连接上的独立 SFTP v3 子系统，由 QSshSession 创建并拥有。
 * 公共方法在所属 Qt 线程调用；网络操作均异步执行，不增加 worker。
 * 非冲突读写可以流水线执行，响应可能乱序；返回零表示未接受请求，否则恰好有一次完成通知。
 * 读写块最多 64 KiB，待处理写入数据合计最多 4 MiB。
 */
class QSshSftpSession : public QObject {
    Q_OBJECT
public:
    /** Closed 为终态，需要重新创建对象才能再次打开。 */
    enum State {
        Idle,    ///< 尚未打开。
        Opening, ///< 正在打开 SSH channel、请求子系统及协商 SFTP 版本。
        Ready,   ///< 可以提交文件和目录请求。
        Closed   ///< 子系统及所有文件句柄均已释放。
    };
    Q_ENUM(State)

    /** 0～8 对应 SFTP v3 状态码，其余为本地错误。 */
    enum Error {
        NoError = 0,          ///< 请求成功。
        EndOfFile = 1,        ///< 文件或目录结尾，正常读取时以成功结果报告。
        NoSuchFile = 2,       ///< 远端路径不存在。
        PermissionDenied = 3, ///< 远端权限不足。
        Failure = 4,          ///< 远端未提供更具体的错误。
        BadMessage = 5,       ///< 远端认为请求格式无效。
        NoConnection = 6,     ///< 远端报告没有连接。
        ConnectionLost = 7,   ///< 远端报告连接丢失。
        Unsupported = 8,      ///< 远端不支持该操作。
        InvalidState = 100,   ///< SSH Session 尚未 Ready 或文件句柄无效。
        ProtocolError,        ///< 版本、响应类型、编号或报文长度无效。
        TimeoutError,         ///< 打开或整个请求超过 Options.requestTimeoutMs。
        SessionClosed,        ///< 所属 SSH Session 已关闭。
        Cancelled             ///< 应用关闭了 SFTP 子系统。
    };
    Q_ENUM(Error)

    /** 文件打开标志，可按位组合；Create/Truncate/Exclusive 必须同时指定 Write。 */
    enum OpenFlag {
        Read = 0x01,     ///< 允许读取。
        Write = 0x02,    ///< 允许写入。
        Append = 0x04,   ///< 写入追加到文件末尾，必须同时指定 Write。
        Create = 0x08,   ///< 文件不存在时创建。
        Truncate = 0x10, ///< 打开时清空文件。
        Exclusive = 0x20 ///< 与 Create 一起使用，路径存在则失败。
    };
    Q_DECLARE_FLAGS(OpenFlags, OpenFlag)
    Q_FLAG(OpenFlags)

    /** FileInfo 中实际存在的属性，服务器可能仅返回其中一部分。 */
    enum Attribute {
        Size = 0x01,         ///< size 有效。
        UserAndGroup = 0x02, ///< uid 和 gid 有效。
        Permissions = 0x04,  ///< permissions 与 mode 有效。
        Times = 0x08         ///< accessTime 和 modificationTime 有效。
    };
    Q_DECLARE_FLAGS(Attributes, Attribute)
    Q_FLAG(Attributes)

    /** 目录项或路径属性；时间为 Unix 秒数，Qt 权限与原始 POSIX mode 分开保存。 */
    struct FileInfo {
        QString name;                         ///< 目录项文件名；stat 时为请求路径。
        QString longName;                     ///< 服务器提供的显示文本；不应解析以获取属性。
        Attributes attributes;                ///< 下列字段的有效性位图。
        quint64 size = 0;                     ///< 文件大小，单位字节。
        quint32 uid = 0;                      ///< 远端用户 ID。
        quint32 gid = 0;                      ///< 远端组 ID。
        QFileDevice::Permissions permissions; ///< Owner/Group/Other 的普通 rwx 权限，不计算当前用户有效权限。
        quint32 mode = 0;                     ///< 原始 POSIX 文件类型、rwx 及 setuid/setgid/sticky 位。
        quint32 accessTime = 0;               ///< 最近访问时间，Unix 秒数。
        quint32 modificationTime = 0;         ///< 最近修改时间，Unix 秒数。
    };

    /** 打开和单个请求的整体时限；长目录按整个遍历计时。 */
    struct Options {
        int requestTimeoutMs = 30000;              ///< 必须大于零，不使用周期轮询。
        qint64 maxInFlightBytes = 4 * 1024 * 1024; ///< 在途读写及尚未交付结果的字节窗口，至少 64 KiB。
    };

    /** 取消全部请求并回收 channel，Session 必须仍然存活。 */
    ~QSshSftpSession() override;
    /** 返回 Qt 线程观察到的状态。 */
    State state() const;
    /** 返回最近的终止错误；单个文件操作错误通过 requestFailed 报告。 */
    Error error() const;
    /** 返回终止错误说明。 */
    QString errorString() const;
    /** 使用默认时限异步打开子系统；所属 SSH Session 必须 Ready。 */
    void open();
    /** 使用 options 异步打开子系统，仅 Idle 接受调用。 */
    void open(const QSshSftpSession::Options &options);
    /** 关闭子系统，所有已接受但未完成的请求以 Cancelled 结束；可重复调用。 */
    void close();

    /** 遍历 path，分批发 directoryEntries，结束后发 requestFinished；不会缓存整个目录。 */
    quint64 listDirectory(const QString &path);
    /** 获取 path 的属性；followSymbolicLinks 为 false 时查询符号链接本身。 */
    quint64 stat(const QString &path, bool followSymbolicLinks = true);
    /** 获取 path 的规范绝对路径，通过 pathResolved 返回。 */
    quint64 canonicalPath(const QString &path);
    /** 获取 path 符号链接保存的目标，通过 pathResolved 返回。 */
    quint64 readLink(const QString &path);
    /** 以 flags 和 Qt permissions 打开 path；User* 输入作为 Owner* 别名，成功发 fileOpened。 */
    quint64 openFile(const QString &path, OpenFlags flags = Read,
                     QFileDevice::Permissions permissions = QFileDevice::ReadOwner | QFileDevice::WriteOwner |
                                                            QFileDevice::ReadGroup | QFileDevice::ReadOther);
    /** 使用原始 POSIX permissions 创建文件，支持 setuid/setgid/sticky；默认推荐 Qt 权限重载。 */
    quint64 openFile(const QString &path, OpenFlags flags, quint32 permissions);

    /** 单项 Qt permission 也按 Qt 权限处理，避免与原始 mode 重载发生整数转换。 */
    quint64 openFile(const QString &path, OpenFlags flags, QFileDevice::Permission permission) {
        return openFile(path, flags, QFileDevice::Permissions(permission));
    }

    /** 关闭本实例的 handle；提交后不得继续使用该句柄。 */
    quint64 closeFile(quint64 handle);
    /** 从 handle 的 offset 读取最多 maximumBytes（1～65536），通过 dataRead 返回；空数据表示 EOF。 */
    quint64 readFile(quint64 handle, quint64 offset, quint32 maximumBytes = 65536);
    /** 向 handle 的 offset 写入 data（1～65536 字节）；完成后发 requestFinished，可据此继续上传。 */
    quint64 writeFile(quint64 handle, quint64 offset, const QByteArray &data);
    /** 以 Qt permissions 创建 path 目录，不递归创建父目录；User* 输入作为 Owner* 别名。 */
    quint64 makeDirectory(const QString &path,
                          QFileDevice::Permissions permissions = QFileDevice::ReadOwner | QFileDevice::WriteOwner |
                                                                 QFileDevice::ExeOwner | QFileDevice::ReadGroup |
                                                                 QFileDevice::ExeGroup | QFileDevice::ReadOther |
                                                                 QFileDevice::ExeOther);
    /** 使用原始 POSIX permissions 创建目录，支持特殊权限位。 */
    quint64 makeDirectory(const QString &path, quint32 permissions);

    /** 使用单项 Qt permission 创建目录。 */
    quint64 makeDirectory(const QString &path, QFileDevice::Permission permission) {
        return makeDirectory(path, QFileDevice::Permissions(permission));
    }

    /** 删除 path 空目录。 */
    quint64 removeDirectory(const QString &path);
    /** 删除 path 文件或符号链接。 */
    quint64 removeFile(const QString &path);
    /** 将 oldPath 重命名为 newPath；按 SFTP v3 语义，目标存在时通常失败。 */
    quint64 rename(const QString &oldPath, const QString &newPath);
    /** 创建 linkPath 符号链接，指向 targetPath；使用 OpenSSH 通行的参数顺序。 */
    quint64 createSymbolicLink(const QString &targetPath, const QString &linkPath);
    /** 将 path 的普通 rwx 位设为 Qt permissions，清除特殊权限位；User* 输入作为 Owner* 别名。 */
    quint64 setPermissions(const QString &path, QFileDevice::Permissions permissions);
    /** 将 path 的权限设为原始 POSIX permissions，包括特殊权限位。 */
    quint64 setPermissions(const QString &path, quint32 permissions);

    /** 使用单项 Qt permission 修改权限。 */
    quint64 setPermissions(const QString &path, QFileDevice::Permission permission) {
        return setPermissions(path, QFileDevice::Permissions(permission));
    }

    /** 异步下载 remotePath 到 localPath，返回由本子系统拥有的传输对象。 */
    QSshSftpTransfer *download(const QString &remotePath, const QString &localPath,
                               const QSshSftpTransfer::Options &options = {});
    /** 异步上传 localPath 到 remotePath；返回后可立即连接进度和完成信号。 */
    QSshSftpTransfer *upload(const QString &localPath, const QString &remotePath,
                             const QSshSftpTransfer::Options &options = {});
    /** 下载到 destination 的当前位置；借用已打开设备，不关闭或删除它，期间须独占使用。 */
    QSshSftpTransfer *download(const QString &remotePath, QIODevice *destination,
                               const QSshSftpTransfer::Options &options = {});
    /** 从 source 当前位置上传；顺序输入结束时调用传输对象的 finishInput()。 */
    QSshSftpTransfer *upload(QIODevice *source, const QString &remotePath,
                             const QSshSftpTransfer::Options &options = {});

Q_SIGNALS:
    /** SFTP v3 协商完成。 */
    void ready();
    /** 生命周期变为 state。 */
    void stateChanged(QSshSftpSession::State state);
    /** 发生子系统级 error，详情见 errorString()；不会关闭所属 SSH Session。 */
    void errorOccurred(QSshSftpSession::Error error);
    /** 子系统资源已释放。 */
    void closed();
    /** requestId 成功结束，在相应结果信号之后发出。 */
    void requestFinished(quint64 requestId);
    /** requestId 失败，error 为类别，message 为说明；其余请求可继续。 */
    void requestFailed(quint64 requestId, QSshSftpSession::Error error, const QString &message);
    /** requestId 的一批目录 entries，可能包括 . 和 ..；以 requestFinished 作为遍历结束标志。 */
    void directoryEntries(quint64 requestId, const QList<QSshSftpSession::FileInfo> &entries);
    /** requestId 返回的路径属性 info。 */
    void fileInfoAvailable(quint64 requestId, const QSshSftpSession::FileInfo &info);
    /** requestId 返回的规范路径或链接目标 path。 */
    void pathResolved(quint64 requestId, const QString &path);
    /** requestId 打开的 handle；句柄仅属于本 SFTP 实例，close 后失效。 */
    void fileOpened(quint64 requestId, quint64 handle);
    /** requestId 读取的 data，最多为请求长度；空数据表示 EOF，短读可继续读取。 */
    void dataRead(quint64 requestId, const QByteArray &data);

private:
    /** 由 session 创建，保持 QObject 父子所有权。 */
    explicit QSshSftpSession(QSshSession *session);
    QScopedPointer<QSshSftpSessionPrivate> d_ptr; ///< 各自的私有实现，隐藏 Asio、libssh 和协议状态。
    Q_DECLARE_PRIVATE(QSshSftpSession)
    Q_DISABLE_COPY(QSshSftpSession)
    friend class QSshSession;
    friend class QSshSftpTransferPrivate;
};

Q_DECLARE_OPERATORS_FOR_FLAGS(QSshSftpSession::OpenFlags)
Q_DECLARE_OPERATORS_FOR_FLAGS(QSshSftpSession::Attributes)
Q_DECLARE_METATYPE(QSshSftpSession::State)
Q_DECLARE_METATYPE(QSshSftpSession::Error)
Q_DECLARE_METATYPE(QSshSftpSession::FileInfo)
Q_DECLARE_METATYPE(QList<QSshSftpSession::FileInfo>)
