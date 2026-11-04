#pragma once

#include <qvfs/qvirtualfilesystem.h>

/**
 * 本机文件系统；接受绝对路径，Windows 盘符和 UNC 路径属于同一个实例。
 * roots() 直接使用 QDir::drives()，其余 I/O 交给 Qt 全局线程池。
 * Qt 目录迭代器不报告遍历中途的系统错误，结果不是目录的原子快照。
 */
class QLocalFileSystem final : public QVirtualFileSystem {
    Q_OBJECT
public:
    /** 创建本机文件系统；parent 为同线程 QObject 所有者，为空时由调用方回收。 */
    explicit QLocalFileSystem(QObject *parent = nullptr);
    /** 返回本机读写、删除和重命名能力；具体路径的权限和挂载限制仍由实际操作判断。 */
    Capabilities capabilities() const override;
    /**
     * 使用 QDir::drives() 获取根路径，延后发送结果，不查询卷标签、容量或可访问性。
     * Windows 返回各盘符根目录，Unix 返回 /；不会枚举全部挂载点或发现 UNC 共享。
     * @param parent Job 的 QObject 所有者；为空时使用本文件系统，须位于同一线程。
     * @return 新建的待完成列表 Job，根条目的类型为 Directory、isRoot 为 true。
     */
    QVirtualListJob *roots(QObject *parent = nullptr) override;
    /**
     * 在线程池查询本地路径本身的属性；符号链接不报告 Qt 跟随目标取得的可选属性。
     * @param path 本机绝对路径，接受的格式与 normalizePath() 一致。
     * @param parent Job 的 QObject 所有者；为空时使用本文件系统，须位于同一线程。
     * @return 新建的待完成属性 Job；路径非法或无法查询时通过 Job 异步报告错误。
     */
    QVirtualStatJob *stat(const QString &path, QObject *parent = nullptr) override;
    /**
     * 在线程池用 Qt 迭代器遍历目录，每批交付完成后才继续生产，不缓存整个目录。
     * 取消会停止后续交付，正在执行的本地系统调用仍可能继续，完成时间不代表原子快照。
     * @param path 本机绝对目录路径，目录路径本身可以是符号链接。
     * @param options 批次上限须在 1..65536；includeHidden 遵循本机 Qt 隐藏项规则。
     * @param parent Job 的 QObject 所有者；为空时使用本文件系统，须位于同一线程。
     * @return 新建的待完成列表 Job；路径、参数和目录访问错误通过 Job 异步报告。
     */
    QVirtualListJob *list(const QString &path, const QVirtualListOptions &options = {},
                          QObject *parent = nullptr) override;

protected:
    /** 为已规范化的 path 创建线程池后台入口；不访问磁盘，根入口标记为不可修改。 */
    std::shared_ptr<qvfs::Backend> backend(const QString &path) const override;
    /**
     * 纯路径规范化，不检查存在性、不解析符号链接；统一分隔符并折叠 . 和 ..。
     * @param path Unix 的 / 绝对路径，或 Windows 的盘符绝对路径、UNC server/share 路径。
     * @return 规范路径，Windows 盘符统一大写；相对路径、空值、空字符及不支持的格式返回 null QString。
     */
    QString normalizePath(const QString &path) const override;
    /**
     * 计算父路径，不访问磁盘。
     * @param path 已规范化的本机绝对路径。
     * @return 父路径；Unix 根、Windows 盘符根及 UNC 共享根返回 null QString。
     */
    QString parentPath(const QString &path) const override;
    /**
     * 拼接直接子项并规范化，不访问磁盘。
     * @param path 已规范化的父路径。
     * @param name 单个名称，不接受空值、.、..、/ 或空字符；Windows 还拒绝反斜杠和冒号。
     * @return 子路径；上述名称校验不通过时返回 null QString，不检查其他本机文件名限制。
     */
    QString childPath(const QString &path, const QString &name) const override;
    /**
     * 提取本机路径名称，不访问磁盘。
     * @param path 已规范化的本机绝对路径。
     * @return 最后一级名称；根入口返回完整根路径。
     */
    QString fileName(const QString &path) const override;
};
