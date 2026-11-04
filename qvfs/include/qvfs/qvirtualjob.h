#pragma once

#include <QObject>
#include <functional>
#include <memory>
#include <qvfs/qvirtualfileinfo.h>

/**
 * 一次异步操作；公开方法、信号及销毁均在所属 Qt 线程使用，该线程需要运行事件循环。
 * 创建后保持 Pending，完成后保留结果；不自动删除，调用方可在 finished() 后 deleteLater()。
 * 销毁会停止后续结果交付，不再发送 finished()；已经开始的后端操作仍可能执行完成。
 */
class QVirtualJob : public QObject {
    Q_OBJECT
public:
    /** 生命周期；除 Pending 外均为终态，进入终态后不再转换状态。 */
    enum State {
        Pending,   ///< 已创建，等待执行、交付结果或完成；也包含正在执行的阶段。
        Succeeded, ///< 操作成功，结果可读取。
        Failed,    ///< 操作失败，详见 error() 和 errorString()。
        Cancelled  ///< 调用方取消了操作，后续不再交付结果。
    };
    Q_ENUM(State)

    /** 后端无关的错误类别；更具体的原始错误码见 backendError()。 */
    enum Error {
        NoError,            ///< 尚未报告错误，或操作已成功；是否完成仍须检查 state()。
        InvalidPath,        ///< 路径格式不符合后端要求，例如传入相对路径或包含空字符。
        InvalidArgument,    ///< 路径以外的参数非法，例如 batchSize 越界。
        NotFound,           ///< 后端未找到路径；本地 Qt 查询无法区分的不可访问路径也可能归入此类。
        PermissionDenied,   ///< 后端报告权限不足。
        NotDirectory,       ///< 要求目录的操作收到非目录路径；后端无法区分时可报告 IoError。
        NotReady,           ///< 后端尚未就绪，或当前状态不接受请求。
        FileSystemClosed,   ///< 文件系统不可用，或底层会话已关闭、断开或销毁。
        Unsupported,        ///< 后端不支持该操作。
        Timeout,            ///< 后端操作超过时限。
        IoError,            ///< 无法归入更具体类别的 I/O 或协议错误。
        OperationCancelled, ///< 调用方取消操作，对应 Cancelled 状态；已确认的部分修改不会撤销。
        AlreadyExists,      ///< 创建或不覆盖重命名遇到已有目标。
        NotEmpty,           ///< 非递归删除遇到非空目录。
        ResourceLimit,      ///< 缓冲、队列或遍历数量超过资源上限。
        CrossFileSystem,    ///< 重命名跨越命名空间或底层挂载点，应改用显式移动。
        OutcomeUnknown,     ///< 修改已经发出，但连接丢失或超时使最终效果无法确认。
        PartialFailure      ///< 复合操作部分完成；详见修改计数及失败路径。
    };
    Q_ENUM(Error)

    /** 停止后续交付并唤醒等待消费的生产者，不等待系统调用结束，也不发送 finished()。 */
    ~QVirtualJob() override;
    /** 返回当前生命周期；只反映所属 Qt 线程已观察到的结果。 */
    State state() const;
    /** 返回是否已进入成功、失败或取消终态。 */
    bool isFinished() const;
    /** 返回错误类别；Pending 和 Succeeded 时均可为 NoError，不能据此判断是否完成。 */
    Error error() const;
    /** 返回终止错误说明；未提供说明或成功时可为空。 */
    QString errorString() const;
    /** 返回后端原始错误码；零表示没有附加错误码，具体含义由后端规定。 */
    int backendError() const;
    /** 返回是否曾请求取消；修改操作可能继续等待确认，最终也可能成功或报告 OutcomeUnknown。 */
    bool isCancellationRequested() const;

public Q_SLOTS:
    /**
     * 请求取消；查询默认立即结束，修改操作由后端确认后结束，重复请求和终态调用无效。
     * 已经开始的系统调用或后端请求仍可能完成，不撤销已确认的副作用。
     */
    void cancel();

Q_SIGNALS:
    /** 进入任一终态时恰好发送一次，包括取消；销毁未完成的 Job 不发送本信号。 */
    void finished();

protected:
    /** 创建 Pending 状态的任务；parent 为同线程的 QObject 所有者，为空时由调用方回收。 */
    explicit QVirtualJob(QObject *parent = nullptr);
    /** 取消扩展点；默认立即以 OperationCancelled 结束，修改任务覆盖后应等待实际结果。 */
    virtual void requestCancel();
    struct Context;                              ///< 后台生产者与 Qt 接收者共享的投递和停止状态，定义隐藏在实现文件中。
    using ContextPtr = std::shared_ptr<Context>; ///< 延长投递状态的生命周期，不延长 Job 本身的生命周期。
    /** 返回共享投递上下文；在 Job 所属线程获取后，可按值交给后台生产者。 */
    ContextPtr context() const;
    /** 线程安全地查询 context 是否已因完成、取消或 Job 销毁而停止；context 必须非空。 */
    static bool isStopped(const ContextPtr &context);
    /**
     * 将回调排队到 Job 所属 Qt 线程；执行前若 Job 已结束或销毁，则丢弃回调。
     * @param context 从 context() 获取的非空共享状态，可在后台线程使用。
     * @param callback 以存活 Job 为参数的回调；不得跨线程捕获并解引用 Job 的裸指针。
     * @param wait 是否等待回调执行完毕或上下文停止；为 true 时只能从后台线程调用。
     * @return 投递时或等待结束时上下文仍未停止则为 true；不保证未来一定执行回调。
     */
    static bool deliver(const ContextPtr &context, std::function<void(QVirtualJob *)> callback, bool wait = false);
    /**
     * 在所属 Qt 线程设置终态、停止后续投递并发送 finished()；重复调用无效。
     * 信号接收者可能立即销毁本 Job，调用后不得假定 this 仍存活。
     * @param error NoError 对应 Succeeded，OperationCancelled 对应 Cancelled，其他对应 Failed。
     * @param message 保存到 errorString() 的说明，可为空。
     * @param backendError 后端原始错误码；零表示未提供。
     */
    void finish(Error error = NoError, QString message = {}, int backendError = 0);

private:
    ContextPtr delivery;                ///< 共享投递状态；后台任务只持有此状态，不持有 Job。
    State currentState = Pending;       ///< 所属 Qt 线程维护的生命周期。
    Error currentError = NoError;       ///< 最近一次终态设置的通用错误类别。
    QString currentMessage;             ///< 终态错误说明。
    int currentBackendError = 0;        ///< 后端附加的原始错误码。
    bool cancellationRequested = false; ///< Qt 线程记录的取消请求，不等同于 Cancelled 终态。
};

/** 删除或重命名任务；失败或取消时仍保留已经完成的修改数量。 */
class QVirtualMutationJob : public QVirtualJob {
    Q_OBJECT
public:
    /** 返回已确认完成的文件/目录修改项数；零不代表 OutcomeUnknown 时没有副作用。 */
    quint64 affectedCount() const;
    /** 返回失败位置；成功或后端未提供具体路径时为空。 */
    QString failedPath() const;

protected:
    /** 创建修改任务；parent 为同线程 QObject 所有者。 */
    explicit QVirtualMutationJob(QObject *parent = nullptr);
    /** 保存结果快照 count/path；须在所属线程调用，不发送信号。 */
    void setResult(quint64 count, QString path);

private:
    quint64 affected = 0; ///< 已确认修改数量。
    QString failed;       ///< 失败路径快照。
};

/** 属性结果在成功 finished 后可读取。 */
class QVirtualStatJob : public QVirtualJob {
    Q_OBJECT
public:
    /** 返回属性快照；仅在 state() 为 Succeeded 时保证结果有效，不执行额外 I/O。 */
    QVirtualFileInfo info() const;

protected:
    /** 创建属性任务；parent 为同线程 QObject 所有者，为空时由调用方回收。 */
    explicit QVirtualStatJob(QObject *parent = nullptr);
    /** 在所属 Qt 线程保存待返回的 info 快照，不发送信号；后端应在成功 finish() 前调用。 */
    void setInfo(QVirtualFileInfo info);

private:
    QVirtualFileInfo result; ///< 保存的属性快照，成功完成后供 info() 读取。
};

/** 根目录和目录枚举共用的流式结果；Job 不额外保存全部目录项。 */
class QVirtualListJob : public QVirtualJob {
    Q_OBJECT
public:
    /** 返回已通过 entriesReady() 交付的条目总数；失败或取消时也保留已交付数量。 */
    quint64 entriesCount() const;
Q_SIGNALS:
    /**
     * 交付一批结果；可能发送多次，空目录可直接 finished()，不发送空批次。
     * @param entries 本批属性快照，不保证排序；需要保留结果时由接收者复制。
     */
    void entriesReady(const QList<QVirtualFileInfo> &entries);

protected:
    /** 创建枚举任务；parent 为同线程 QObject 所有者，为空时由调用方回收。 */
    explicit QVirtualListJob(QObject *parent = nullptr);
    /**
     * 在所属 Qt 线程累计数量并发送 entriesReady()；空批次或任务已结束时不执行任何操作。
     * @param entries 当前批次快照，不保存到 Job；信号接收者可能立即取消或销毁本对象。
     */
    void publish(const QList<QVirtualFileInfo> &entries);

private:
    quint64 count = 0; ///< 已发送的条目累计数量，不包含被过滤或取消交付的条目。
};
