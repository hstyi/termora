#pragma once

#include <QPointer>
#include <qvfs/qvirtualfilesystem.h>

class QSshSftpSession;

/**
 * 已打开的 SFTP 子系统的 VFS 适配，沿用 qssh 的 Asio 执行器，不增加线程池。
 * 借用 session，二者必须在同一 Qt 线程；销毁文件系统不会关闭子系统。
 * 路径使用远端 POSIX 绝对路径，roots() 提供 /。本类不负责认证或打开子系统。
 * 查询取消立即停止结果交付；修改操作取消后等待实际确认，断线时可能报告 OutcomeUnknown。
 * 本地路径复制复用 qssh 流水线；实际窗口还受 SFTP Options.maxInFlightBytes 和 SSH 流控约束。
 */
class QSftpFileSystem final : public QVirtualFileSystem {
    Q_OBJECT
public:
    /**
     * 为现有 SFTP 子系统创建适配器，不发起连接、不打开子系统，也不接管子系统所有权。
     * @param session 借用的同线程子系统；操作前须已 Ready，为空或销毁后操作以 FileSystemClosed 结束。
     * @param parent 本适配器的同线程 QObject 所有者，为空时由调用方回收。
     */
    explicit QSftpFileSystem(QSshSftpSession *session, QObject *parent = nullptr);
    /** 销毁本适配器拥有的引用和 Job，不关闭借用的 SFTP 子系统。 */
    ~QSftpFileSystem() override;
    /** 返回 SFTP v3 的读写、追加、删除和重命名能力，不保证覆盖重命名或原子替换。 */
    Capabilities capabilities() const override;
    /**
     * 在子系统 Ready 时异步提供单个 / 根入口，不提交远端目录或属性请求。
     * @param parent Job 的 QObject 所有者；为空时使用本适配器，须位于同一线程。
     * @return 新建的待完成列表 Job；未就绪或子系统不可用时通过 Job 报告错误。
     */
    QVirtualListJob *roots(QObject *parent = nullptr) override;
    /**
     * 使用 SFTP lstat 异步查询路径本身，不跟随最后一级符号链接，不补查服务器未报告的属性。
     * @param path 远端 POSIX 绝对路径，与客户端操作系统的路径格式无关。
     * @param parent Job 的 QObject 所有者；为空时使用本适配器，须位于同一线程。
     * @return 新建的待完成属性 Job；远端请求错误保留 SFTP 原始错误码到 backendError()。
     */
    QVirtualStatJob *stat(const QString &path, QObject *parent = nullptr) override;
    /**
     * 复用 SFTP 的目录批次流异步列举子项，不递归，不包含 . 和 ..，结果不保证排序。
     * 取消仅停止本 Job 交付；已提交的请求仍可能完成，不关闭共享子系统或影响其他任务。
     * @param path 远端 POSIX 绝对目录路径。
     * @param options 批次上限须在 1..65536；includeHidden 为 false 时过滤名称以点开头的项。
     * @param parent Job 的 QObject 所有者；为空时使用本适配器，须位于同一线程。
     * @return 新建的待完成列表 Job；状态、参数及远端错误均在返回后通过 Job 报告。
     */
    QVirtualListJob *list(const QString &path, const QVirtualListOptions &options = {},
                          QObject *parent = nullptr) override;

protected:
    /** 为已规范化的 path 创建后台入口，通过子系统的安全分发器复用 Asio worker。 */
    std::shared_ptr<qvfs::Backend> backend(const QString &path) const override;
    /** 为本地/SFTP 的 source→target 选择原生传输；options 契约不满足时异步报错，parent 管理任务。 */
    QVirtualTransferJob *tryCopy(QVirtualFile *source, QVirtualFile *target, const QVirtualTransferOptions &options,
                                 QObject *parent) override;
    /**
     * 按远端 POSIX 规则折叠分隔符、. 和 ..，不访问服务器、不解析符号链接。
     * @param path 以 / 开头的路径；反斜杠视为普通名称字符。
     * @return 规范化的绝对路径；相对路径或包含空字符时返回 null QString。
     */
    QString normalizePath(const QString &path) const override;
    /**
     * 按远端规则计算父级，不访问服务器。
     * @param path 已规范化的远端绝对路径。
     * @return 父路径；/ 没有父级，返回 null QString。
     */
    QString parentPath(const QString &path) const override;
    /**
     * 拼接远端直接子项，不访问服务器，也不检查目标存在性。
     * @param path 已规范化的远端父路径。
     * @param name 单个名称；不接受空值、.、..、/ 或空字符，允许反斜杠。
     * @return 子路径；名称非法时返回 null QString。
     */
    QString childPath(const QString &path, const QString &name) const override;
    /**
     * 提取远端路径名称，不访问服务器。
     * @param path 已规范化的远端绝对路径。
     * @return 最后一级名称；根路径返回 /。
     */
    QString fileName(const QString &path) const override;

private:
    QPointer<QSshSftpSession> session; ///< 借用的 SFTP 子系统，销毁后自动清空，不随本适配器销毁而关闭。
};
