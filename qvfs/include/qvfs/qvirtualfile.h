#pragma once

#include <QObject>
#include <QPointer>
#include <qvfs/qvirtualfilesystem.h>

/**
 * 由 QVirtualFileSystem::file() 创建的资源引用，调用方不直接构造。
 * 路径操作不访问后端；引用不持有打开的文件句柄，任务状态由各次操作的 Job 保存。
 * 所有方法及销毁均在所属 Qt 线程执行，须与所属文件系统位于同一线程。
 * 引用不延长文件系统的生命周期；外部 parent 拥有的引用可以在文件系统销毁后继续存活。
 */
class QVirtualFile : public QObject {
    Q_OBJECT
public:
    /** 销毁本引用及其拥有的 Job，不删除后端资源；外部 parent 拥有的 Job 不受影响。 */
    ~QVirtualFile() override;
    /** 返回创建时规范化的后端路径，不检查存在性；文件系统销毁后仍可读取。 */
    QString path() const;
    /** 返回后端规则下的文件名或根名称；文件系统已销毁时返回空字符串，不访问后端。 */
    QString name() const;
    /** 返回借用的文件系统指针，不转移所有权；文件系统已销毁时返回 nullptr。 */
    QVirtualFileSystem *fileSystem() const;
    /**
     * 创建父路径的引用，不检查父路径存在性；与 QObject::parent() 的所有权关系无关。
     * @param parent 新引用的 QObject 所有者；为空时使用所属文件系统，须位于同一线程。
     * @return 新父路径引用；本路径是根入口或文件系统已销毁时返回 nullptr。
     */
    QVirtualFile *parentFile(QObject *parent = nullptr) const;
    /**
     * 创建直接子项引用，不要求本路径为已存在的目录。
     * @param name 单个子项名称；不接受空值、.、..、路径分隔符或后端禁止的字符。
     * @param parent 新引用的 QObject 所有者；为空时使用所属文件系统，须位于同一线程。
     * @return 新子项引用；名称非法或文件系统已销毁时返回 nullptr。
     */
    QVirtualFile *child(const QString &name, QObject *parent = nullptr) const;
    /**
     * 异步查询本路径的属性，不跟随最后一级符号链接；成功后读取 Job 的 info()。
     * @param parent Job 的 QObject 所有者；为空时使用本引用，须位于同一线程。
     * @return 新建的待完成 Job；文件系统已销毁时以 FileSystemClosed 异步结束。
     */
    virtual QVirtualStatJob *stat(QObject *parent = nullptr) = 0;
    /**
     * 异步分批列举直接子项；不递归、不排序，不包含 . 和 ..。
     * @param options 单批交付上限及隐藏项过滤配置，规则同文件系统的 list()。
     * @param parent Job 的 QObject 所有者；为空时使用本引用，须位于同一线程。
     * @return 新建的待完成 Job；文件系统已销毁时以 FileSystemClosed 异步结束。
     */
    virtual QVirtualListJob *list(const QVirtualListOptions &options = {}, QObject *parent = nullptr) = 0;
    /**
     * 异步打开本路径，引用本身不保存打开状态。
     * @param options 访问权限、创建、追加及原子替换策略，规则同文件系统的 open()。
     * @param parent Job 的同线程所有者；为空使用本引用，领取的句柄默认由文件系统拥有。
     * @return 待完成打开任务；成功后调用 takeHandle()，文件系统已销毁时异步报告 FileSystemClosed。
     */
    QVirtualOpenJob *open(const QVirtualOpenOptions &options = {}, QObject *parent = nullptr);
    /**
     * 异步删除本路径，不跟随最后一级链接，也不删除根入口。
     * @param options 递归及忽略不存在策略；默认仅删除文件、链接或空目录。
     * @param parent Job 的同线程所有者；为空使用本引用。
     * @return 待完成修改任务；部分完成时可查询 affectedCount() 和 failedPath()。
     */
    QVirtualMutationJob *remove(const QVirtualRemoveOptions &options = {}, QObject *parent = nullptr);
    /**
     * 在同一文件系统实例内重命名到 target，不更改本引用路径。
     * @param target 同线程目标引用，必须属于同一文件系统；跨实例需用 moveTo()。
     * @param options 覆盖及原子要求，后端不能保证时返回 Unsupported。
     * @param parent Job 的同线程所有者；为空使用本引用。
     * @return 待完成修改任务；重命名成功后应使用 target 访问新路径。
     */
    QVirtualMutationJob *renameTo(QVirtualFile *target, const QVirtualRenameOptions &options = {},
                                  QObject *parent = nullptr);
    /**
     * 异步复制普通文件，后端自动选择专用通路或通用后台流水线。
     * @param target 同线程目标引用，不接管所有权；调用期间保存其路径和后端状态。
     * @param options 覆盖、原子提交、缓冲和空闲时限配置，规则同文件系统的 copy()。
     * @param parent Job 的同线程所有者；为空使用本引用。
     * @return 待完成传输任务；100% 字节进度后仍须等待最终 finished() 确认提交。
     */
    QVirtualTransferJob *copyTo(QVirtualFile *target, const QVirtualTransferOptions &options = {},
                                QObject *parent = nullptr);
    /**
     * 完整复制并提交目标后再删除源，不自动递归目录，也不修改本引用的路径。
     * @param target 同线程目标引用，所有权仍归调用方；复制通路与 copyTo() 相同。
     * @param options 完整复制策略，规则同文件系统的 move()。
     * @param parent Job 的同线程所有者；为空使用本引用。
     * @return 待完成传输任务；删源失败保留目标，报告 PartialFailure 或 OutcomeUnknown。
     */
    QVirtualTransferJob *moveTo(QVirtualFile *target, const QVirtualTransferOptions &options = {},
                                QObject *parent = nullptr);

protected:
    /**
     * 初始化由工厂提供的引用，不访问后端。
     * @param fileSystem 借用的后端文件系统，不接管所有权，须位于同一线程。
     * @param path 已按后端规则规范化的路径，在引用生命周期内保持不变。
     * @param parent 引用的 QObject 所有者；为空时由调用方回收，构造函数不替换默认父对象。
     */
    QVirtualFile(QVirtualFileSystem *fileSystem, QString path, QObject *parent);

private:
    QPointer<QVirtualFileSystem> owner; ///< 文件系统的非拥有引用，后端销毁后自动清空。
    const QString location;             ///< 规范化的路径标识；不会随远端重命名或删除而变化。
};
