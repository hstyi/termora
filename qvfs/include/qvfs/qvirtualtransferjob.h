#pragma once

#include <qvfs/qvirtualfileoptions.h>
#include <qvfs/qvirtualjob.h>

namespace qvfs {
    class Backend;
    struct Transfer;
} // namespace qvfs
class QVirtualFileSystem;

/** 完整单文件传输；内容在后台流转，Qt 线程只接收合并的进度快照和最终结果。 */
class QVirtualTransferJob : public QVirtualJob {
    Q_OBJECT
public:
    /** 传输阶段；达到总字节数后仍须等待提交阶段成功。 */
    enum Phase {
        Preparing,    ///< 校验端点、预留预算并打开源和目标。
        Transferring, ///< 多块内容在流水线传输。
        Committing    ///< 等待关闭、最终提交，以及移动时的源删除。
    };
    Q_ENUM(Phase)
    /** 停止补发并安排后台清理，不等待清理完成。 */
    ~QVirtualTransferJob() override;
    /** 返回目标已确认处理的字节数，不将源预读量计入进度。 */
    qint64 bytesTransferred() const;
    /** 返回总字节数快照，-1 为未知。 */
    qint64 totalBytes() const;
    /** 返回当前阶段；整体结果以 finished() 后的 state() 为准。 */
    Phase phase() const;
    /** 设置进程内 VFS 传输总缓冲预算 limit，至少 64 KiB；缩小时不终止现有任务，线程安全。 */
    static bool setGlobalBufferLimit(qint64 limit);
    /** 返回进程内 VFS 传输缓冲预算上限，默认 256 MiB，线程安全。 */
    static qint64 globalBufferLimit();
Q_SIGNALS:
    /** completed 为目标确认字节数，total 为总量或 -1；通知合并，100% 不代表提交完成。 */
    void progress(qint64 completed, qint64 total);
    /** 后台进入 phase 阶段；此信号只在 Qt 线程发送。 */
    void phaseChanged(QVirtualTransferJob::Phase phase);

protected:
    /** 创建传输任务，parent 为同线程 QObject 所有者，为空时由调用方回收。 */
    explicit QVirtualTransferJob(QObject *parent = nullptr);
    /** 请求停止传输；已提交的修改不能撤销，后端确认后才进入终态。 */
    void requestCancel() override;
    /** 在 Qt 线程更新进度 completed/total 及阶段 phase，信号可能导致本对象销毁。 */
    void report(qint64 completed, qint64 total, Phase phase);
    /** 为 options.bufferSize 预留全局预算；失败时应异步报告 ResourceLimit。 */
    std::shared_ptr<void> reserveBuffer(const QVirtualTransferOptions &options);

private:
    /** 为 source→target 创建后台流水线；options 为配置快照，所有结果通过 Qt 线程交付。 */
    void start(std::shared_ptr<qvfs::Backend> source, std::shared_ptr<qvfs::Backend> target,
               QVirtualTransferOptions options);
    std::shared_ptr<qvfs::Transfer> operation; ///< 后台协调器；迟到回调保留其生命周期。
    qint64 completed = 0, total = -1;          ///< Qt 线程的进度快照。
    Phase currentPhase = Preparing;            ///< Qt 线程的阶段快照。
    friend class QVirtualFileSystem;
};
