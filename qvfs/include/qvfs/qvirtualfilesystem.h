#pragma once

#include <QObject>
#include <qvfs/qvirtualfilehandle.h>
#include <qvfs/qvirtualjob.h>
#include <qvfs/qvirtualtransferjob.h>

class QVirtualFile;

/** 单次目录遍历选项；后端按值保存，调用后修改选项不影响已创建的 Job。 */
struct QVirtualListOptions {
    int batchSize = 128;       ///< 单批交付上限，1..65536；结果不保证排序。
    bool includeHidden = true; ///< 是否包含隐藏项；本地遵循 Qt 隐藏属性，SFTP 按文件名以点开头判断。
};

/**
 * 一个后端命名空间，可含多个根入口；公开接口和对象销毁均在所属 Qt 线程执行。
 * file() 只创建路径引用，访问后端的操作通过 Job 交付结果，所属线程需要运行事件循环。
 * 默认由本对象拥有创建的文件引用及直接创建的 Job；调用方可用 parent 指定其他所有者。
 * Job 不自动删除，使用完成后由所有者回收或调用 deleteLater()。
 */
class QVirtualFileSystem : public QObject {
    Q_OBJECT
public:
    /** 后端可提供的操作能力，不表示当前用户一定有权限；实际操作仍可能失败。 */
    enum Capability {
        Read = 0x01,          ///< 能打开并读取内容。
        Write = 0x02,         ///< 能打开并写入内容。
        RandomRead = 0x04,    ///< 支持显式偏移读取。
        RandomWrite = 0x08,   ///< 支持显式偏移修改已有内容。
        Append = 0x10,        ///< 支持追加写入。
        Remove = 0x20,        ///< 支持删除文件和目录。
        Rename = 0x40,        ///< 支持同命名空间重命名。
        AtomicReplace = 0x80, ///< 支持关闭时原子发布完整替换内容。
        AtomicRename = 0x100  ///< 可要求原子重命名，跨挂载点仍可能失败。
    };
    Q_DECLARE_FLAGS(Capabilities, Capability)
    Q_FLAG(Capabilities)
    /** 销毁本对象拥有的文件引用和 Job；使用外部 parent 的对象保留各自生命周期。 */
    ~QVirtualFileSystem() override;
    /**
     * 解析路径并创建新的文件引用，不访问后端，也不要求目标存在。
     * @param path 后端命名空间内的路径，接受的格式由具体文件系统规定。
     * @param parent 引用的 QObject 所有者；为空时使用本文件系统，须与本对象位于同一线程。
     * @return 新引用；非法路径返回 nullptr，相同路径的多次调用不会复用引用对象。
     */
    QVirtualFile *file(const QString &path, QObject *parent = nullptr);
    /**
     * 异步交付根入口快照；结果通过 entriesReady() 分批发送，以 finished() 结束。
     * 根入口不保证当前可访问；读取结果中的路径后，可用 file() 创建对应引用。
     * @param parent Job 的 QObject 所有者；为空时使用本文件系统，须位于同一线程。
     * @return 新建的待完成 Job；结果信号在本方法返回后发送，错误通过 Job 报告。
     */
    virtual QVirtualListJob *roots(QObject *parent = nullptr) = 0;
    /**
     * 异步查询路径本身的属性，不跟随最后一级符号链接；成功后读取 Job 的 info()。
     * @param path 后端路径；格式与 file() 一致，目标不存在时 Job 以 NotFound 结束。
     * @param parent Job 的 QObject 所有者；为空时使用本文件系统，须位于同一线程。
     * @return 新建的待完成 Job；非法路径也通过返回后的完成信号报告，不返回 nullptr。
     */
    virtual QVirtualStatJob *stat(const QString &path, QObject *parent = nullptr) = 0;
    /**
     * 流式列举目录的直接子项，不递归、不排序，不包含 . 和 ..；目录路径本身可以是符号链接。
     * 已交付部分结果后仍可能失败，应以 finished() 时的 Job 状态判断整体结果。
     * @param path 待遍历的后端目录路径，格式与 file() 一致。
     * @param options 单批交付上限及隐藏项过滤配置；非法配置通过 Job 报告。
     * @param parent Job 的 QObject 所有者；为空时使用本文件系统，须位于同一线程。
     * @return 新建的待完成 Job；entriesReady() 和 finished() 均在本方法返回后发送。
     */
    virtual QVirtualListJob *list(const QString &path, const QVirtualListOptions &options = {},
                                  QObject *parent = nullptr) = 0;
    /** 返回后端的潜在操作能力，不访问后端；默认无内容或修改能力。 */
    virtual Capabilities capabilities() const;
    /**
     * 异步打开路径；不支持的模式不隐式模拟。
     * @param path 后端路径，格式同 file()。
     * @param options 访问、创建、追加和原子替换策略。
     * @param parent Job 的同线程所有者；为空使用本文件系统。
     * @return 待完成打开任务，成功后调用 takeHandle() 领取句柄。
     */
    QVirtualOpenJob *open(const QString &path, const QVirtualOpenOptions &options = {}, QObject *parent = nullptr);
    /**
     * 异步删除路径本身，禁止删除根入口，不跟随符号链接。
     * @param path 后端路径，格式同 file()。
     * @param options 递归及忽略不存在策略。
     * @param parent Job 的同线程所有者；为空使用本文件系统。
     * @return 待完成修改任务，部分完成时保留 affectedCount()/failedPath()。
     */
    QVirtualMutationJob *remove(const QString &path, const QVirtualRemoveOptions &options = {},
                                QObject *parent = nullptr);
    /**
     * 异步重命名，不退化成跨挂载点复制；本文件系统内路径引用不会随之改变。
     * @param source 原后端路径，不能是根入口。
     * @param target 目标后端路径，不能是根入口。
     * @param options 覆盖及要求原子的策略，无法满足时报告 Unsupported。
     * @param parent Job 的同线程所有者；为空使用本文件系统。
     * @return 待完成修改任务，连接中断后不能确定结果时报告 OutcomeUnknown。
     */
    QVirtualMutationJob *rename(const QString &source, const QString &target, const QVirtualRenameOptions &options = {},
                                QObject *parent = nullptr);
    /**
     * 异步复制一个普通文件，优先由后端选择专用传输通路。
     * @param source 本文件系统内的源路径。
     * @param target 同线程目标引用，调用期间复制其路径和后端状态，不接管所有权。
     * @param options 覆盖、原子提交、缓冲和空闲时限配置。
     * @param parent Job 的同线程所有者；为空使用本文件系统。
     * @return 待完成传输任务，内容不会逐块交给 GUI 调度。
     */
    QVirtualTransferJob *copy(const QString &source, QVirtualFile *target, const QVirtualTransferOptions &options = {},
                              QObject *parent = nullptr);
    /**
     * 显式跨命名空间移动：完整复制并关闭目标后才删除源，不自动递归目录。
     * @param source 本文件系统内的源路径。
     * @param target 同线程目标文件引用，不接管所有权。
     * @param options 复制策略，与 copy() 相同。
     * @param parent Job 的同线程所有者；为空使用本文件系统。
     * @return 待完成传输任务；复制成功而删源失败时报告 PartialFailure，保留两边文件。
     */
    QVirtualTransferJob *move(const QString &source, QVirtualFile *target, const QVirtualTransferOptions &options = {},
                              QObject *parent = nullptr);

protected:
    /** 初始化后端基类；parent 为 QObject 所有者，须与创建线程一致，为空时由调用方回收。 */
    explicit QVirtualFileSystem(QObject *parent = nullptr);
    /**
     * 纯路径解析，不访问后端、不解析符号链接。
     * @param path 调用方提供的后端路径。
     * @return 后端统一格式的路径；null QString 表示非法，非 null 的空字符串可作为后端的根。
     */
    virtual QString normalizePath(const QString &path) const = 0;
    /**
     * 纯路径计算父级，不访问后端。
     * @param path 已由 normalizePath() 处理的路径。
     * @return 父路径；根入口无父级时返回 null QString。
     */
    virtual QString parentPath(const QString &path) const = 0;
    /**
     * 纯路径计算直接子项，不要求父路径或子项存在。
     * @param path 已由 normalizePath() 处理的父路径。
     * @param name 单个子项名称，接受的字符由后端规定，不能作为跨级导航路径。
     * @return 子路径；非法名称返回 null QString。
     */
    virtual QString childPath(const QString &path, const QString &name) const = 0;
    /**
     * 从路径中提取名称，不访问后端。
     * @param path 已由 normalizePath() 处理的路径。
     * @return 文件名或根入口的名称，显示规则由后端规定。
     */
    virtual QString fileName(const QString &path) const = 0;
    /** 为已规范化的 path 创建后台入口；不得访问后端，默认返回 Unsupported 入口。 */
    virtual std::shared_ptr<qvfs::Backend> backend(const QString &path) const;
    /**
     * 专用复制扩展点；仅在 Qt 线程选择通路，数据必须在后台传输。
     * @param source 源引用，方法内可读取路径，不能将裸指针保留到异步操作中。
     * @param target 目标引用，生命周期规则同 source。
     * @param options 必须完整满足的复制契约。
     * @param parent 返回任务的同线程所有者。
     * @return 专用任务；nullptr 表示交给通用流水线，不表示操作失败。
     */
    virtual QVirtualTransferJob *tryCopy(QVirtualFile *source, QVirtualFile *target,
                                         const QVirtualTransferOptions &options, QObject *parent);
    friend class QVirtualFile;
};
Q_DECLARE_OPERATORS_FOR_FLAGS(QVirtualFileSystem::Capabilities)
