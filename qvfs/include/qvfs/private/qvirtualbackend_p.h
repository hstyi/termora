#pragma once

// 后端实现契约，不是应用层 API；公开头文件不依赖 Asio、libssh 或具体设备。
#include <atomic>
#include <deque>
#include <mutex>
#include <qvfs/qvirtualfileoptions.h>
#include <qvfs/qvirtualjob.h>

class QVirtualOpenJob;
class QVirtualFileSystem;
class QVirtualTransferJob;

namespace qvfs {
    class Handle;

    /** 跨线程取消令牌；取消只阻止尚未发出的操作，不撤销已确认的修改。 */
    struct Control {
        std::atomic_bool cancelled = false;
    };

    using ControlPtr = std::shared_ptr<Control>; ///< 每项操作独立的取消状态。

    /** 后台结果；回调不得解引用任何前端 QObject。 */
    struct Result {
        QVirtualJob::Error error = QVirtualJob::NoError; ///< 通用错误类别。
        QString message;                                 ///< 错误说明。
        int nativeError = 0;                             ///< 后端原始错误码。
        QByteArray data;                                 ///< 读取结果。
        bool eof = false;                                ///< 已明确到达 EOF。
        qint64 size = -1;                                ///< 打开时大小，-1 为未知。
        quint64 affected = 0;                            ///< 已完成的修改项数或写入字节数。
        QString failedPath;                              ///< 递归操作失败位置。
        std::shared_ptr<Handle> handle;                  ///< 成功打开的后台句柄。
    };

    using Callback = std::function<void(Result)>; ///< 后端必须恰好调用一次，可在任意后台线程执行。

    /** 在共享 Qt 线程池串行执行短任务，不为每个对象创建线程，不阻塞等待后端响应。 */
    class SerialQueue : public std::enable_shared_from_this<SerialQueue> {
    public:
        /** 将 task 排队执行；任务不得等待后续同队列任务或 Qt 前端信号。 */
        void post(std::function<void()> task);

    private:
        void drain();                            ///< 执行一个有限批次后让出线程池。
        std::mutex mutex;                        ///< 保护队列及 running。
        std::deque<std::function<void()>> tasks; ///< 待执行的后台短任务。
        bool running = false;                    ///< 是否已有 drain 排队或执行。
    };

    /** 不含 QObject 的后台句柄；请求的排队、冲突排序与实际 I/O 全由实现负责。 */
    class Handle {
    public:
        virtual ~Handle() = default;

        /** 是否可按非连续偏移并行写入；默认顺序目标，追加句柄也必须返回 false。 */
        virtual bool randomWrite() const {
            return false;
        }

        /** 读取 offset 起最多 size 字节；control 取消时只阻止尚未发出的请求，callback 必须完成。 */
        virtual void read(quint64 offset, quint32 size, ControlPtr control, Callback callback) = 0;
        /** 整块写入 offset/data；回调成功必须确认整块，不能仅表示进入无界队列。 */
        virtual void write(quint64 offset, QByteArray data, ControlPtr control, Callback callback) = 0;
        /** 等待已接受请求并关闭；commit=false 放弃暂存内容，commit=true 尝试最终发布。 */
        virtual void close(bool commit, Callback callback) = 0;
    };

    /** 某个规范化路径的后台入口，可独立于前端引用存活；不能反向调用 GUI 来推进内容传输。 */
    class Backend {
    public:
        virtual ~Backend() = default;
        /** 取得路径所属命名空间标识；相同标识才可尝试直接重命名。 */
        virtual const void *identity() const = 0;
        /** 返回规范化路径快照，用于同路径校验及失败报告。 */
        virtual QString path() const = 0;

        /** 复制前校验已知的源/目标别名，不修改资源；默认不能进一步判断，仍执行后端打开校验。 */
        virtual void verifyCopyTarget(std::shared_ptr<Backend>, ControlPtr, Callback callback) {
            callback({});
        }

        /** 异步按 options 打开；callback 成功时返回后台句柄和可用大小。 */
        virtual void open(QVirtualOpenOptions options, ControlPtr control, Callback callback) = 0;
        /** 按 options 删除；根校验必须在任何修改前完成，报告已修改数量及失败位置。 */
        virtual void remove(QVirtualRemoveOptions options, ControlPtr control, Callback callback) = 0;
        /** 在同一命名空间内重命名到 target，不能隐式复制；策略无法满足时报告 Unsupported。 */
        virtual void rename(QString target, QVirtualRenameOptions options, ControlPtr control, Callback callback) = 0;
    };

    /** 检查打开组合；返回空说明表示合法，非空为参数错误。 */
    QString validateOpen(const QVirtualOpenOptions &options);
    /** 创建统一错误结果，code 为类别，message 为说明。 */
    Result failure(QVirtualJob::Error code, QString message);
    /** 为本机规范路径 path 创建后端，root 表明此路径为不可修改的根入口。 */
    std::shared_ptr<Backend> localBackend(QString path, bool root);
    /** 创建恒定失败的异步入口，用于非法路径、后端已销毁及不支持的操作。 */
    std::shared_ptr<Backend> rejectedBackend(QVirtualJob::Error error, QString message);
    /** 创建打开任务，owner 为默认句柄所有者，parent 为任务所有者。 */
    QVirtualOpenJob *openJob(QVirtualFileSystem *owner, std::shared_ptr<Backend> backend, QVirtualOpenOptions options,
                             QObject *parent);
    /** 创建修改任务；start 必须将后台结果恰好交付一次，parent 为任务所有者。 */
    QVirtualMutationJob *mutationJob(std::function<void(ControlPtr, Callback)> start, QObject *parent);
    /** 创建异步失败传输任务，error/message 为失败结果，parent 为所有者。 */
    QVirtualTransferJob *failedTransfer(QVirtualJob::Error error, QString message, QObject *parent);
} // namespace qvfs
