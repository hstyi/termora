#pragma once

#include <QByteArray>
#include <qvfs/qvirtualfileoptions.h>
#include <qvfs/qvirtualjob.h>

namespace qvfs {
    class Handle;
    class Backend;
    struct Control;
} // namespace qvfs
class QVirtualFileSystem;

/** 单块读取任务；成功后读取 data() 和 atEnd()，短读不自动代表 EOF。 */
class QVirtualReadJob : public QVirtualJob {
    Q_OBJECT
public:
    /** 请求停止尚未发出的读取，丢弃迟到结果。 */
    ~QVirtualReadJob() override;
    /** 返回本块数据快照；仅成功时有效，不执行后端 I/O。 */
    QByteArray data() const;
    /** 返回后端是否明确报告 EOF；空数据本身不等于 EOF，应同时检查本标记和任务错误。 */
    bool atEnd() const;

private:
    /** 由句柄创建读取任务；parent 为同线程 Job 所有者。 */
    explicit QVirtualReadJob(QObject *parent);
    QByteArray contents;                    ///< 已读取的数据，随 Job 销毁释放。
    bool eof = false;                       ///< 明确的 EOF 标记。
    std::shared_ptr<qvfs::Control> control; ///< 独立的后台取消令牌。
    void requestCancel() override;          ///< 停止后台补发并立即结束结果交付。
    friend class QVirtualFileHandle;
};

/** 单块写入任务；成功表示整块按句柄契约处理，不代表最终提交或持久化。 */
class QVirtualWriteJob : public QVirtualJob {
    Q_OBJECT
public:
    /** 请求取消尚未发出的写入；已发出的写入可能生效。 */
    ~QVirtualWriteJob() override;
    /** 返回本块成功写入的字节数；发生错误时不用于推断远端实际修改范围。 */
    qint64 bytesWritten() const;

private:
    /** 由句柄创建写入任务；parent 为同线程 Job 所有者。 */
    explicit QVirtualWriteJob(QObject *parent);
    qint64 written = 0;                     ///< 成功完成的本块字节数。
    std::shared_ptr<qvfs::Control> control; ///< 写入取消请求，等待后端确认终态。
    void requestCancel() override;          ///< 设置取消令牌，不立即声称写入已撤销。
    friend class QVirtualFileHandle;
};

/**
 * 一次打开的资源，公开方法和销毁在所属 Qt 线程执行；实际读写由后端调度。
 * 不同偏移的请求可并行，冲突请求由后端保持顺序；close() 拒绝新请求并等待此前请求。
 * 析构只安排异步关闭/放弃，不能代替显式 close() 获取提交结果，原子替换不会在析构时提交。
 */
class QVirtualFileHandle final : public QObject {
    Q_OBJECT
public:
    /** 放弃尚未提交的内容并异步回收资源，不阻塞调用线程。 */
    ~QVirtualFileHandle() override;
    /** 返回是否已提交关闭或放弃请求；不代表后台已完成回收。 */
    bool isClosing() const;
    /** 返回打开时的大小快照，-1 为未知；不随后续读写自动更新。 */
    qint64 initialSize() const;
    /**
     * 异步读取一块；同一句柄可以同时接受多个非冲突请求。
     * @param offset 无符号字节偏移，超出后端范围时返回错误。
     * @param maximumBytes 请求上限，1..65536；成功结果可以短于此值。
     * @param parent Job 所有者；为空使用本句柄，须位于同一 Qt 线程。
     * @return 待完成 Job，错误也通过返回后的 finished() 报告。
     */
    QVirtualReadJob *readAt(quint64 offset, quint32 maximumBytes = 65536, QObject *parent = nullptr);
    /**
     * 异步写入整块；底层短写由后端继续完成，追加模式忽略 offset 的定位作用。
     * @param offset 字节偏移，不得与长度相加溢出。
     * @param data 1..65536 字节，以共享/移动缓冲保存到请求结束。
     * @param parent Job 所有者；为空使用本句柄，须位于同一 Qt 线程。
     * @return 待完成 Job；已发送的写入取消后仍可能生效，需检查最终结果。
     */
    QVirtualWriteJob *writeAt(quint64 offset, QByteArray data, QObject *parent = nullptr);
    /**
     * 异步等待此前请求并关闭；原子替换的最终发布也在此操作完成，不承诺 fsync。
     * @param parent Job 所有者；为空使用本句柄，须位于同一 Qt 线程。
     * @return 待完成 Job；重复关闭返回 InvalidArgument，关闭后不再接受读写。
     */
    QVirtualJob *close(QObject *parent = nullptr);

private:
    /** 接管已打开的 backend；size 为初始大小或 -1，parent 为同线程所有者。 */
    QVirtualFileHandle(std::shared_ptr<qvfs::Handle> backend, qint64 size, QObject *parent);
    std::shared_ptr<qvfs::Handle> backend; ///< 可独立于前端存活的后台句柄。
    qint64 size;                           ///< 打开时大小快照，-1 表示未知。
    bool closing = false;                  ///< Qt 线程维护的关闭屏障。
    friend class QVirtualOpenJob;
};

/** 打开任务；未领取的句柄随 Job 销毁异步回收。 */
class QVirtualOpenJob : public QVirtualJob {
    Q_OBJECT
public:
    /**
     * 成功后领取句柄，只能领取一次；领取后可安全销毁 OpenJob。
     * @param parent 新句柄的同线程 QObject 所有者；为空时使用创建任务的文件系统。
     * @return 新句柄；任务未成功、已经领取或默认所有者已销毁时返回 nullptr。
     */
    QVirtualFileHandle *takeHandle(QObject *parent = nullptr);

protected:
    /** 创建打开任务；owner 为默认句柄所有者，parent 为任务所有者，均须在同一线程。 */
    QVirtualOpenJob(QVirtualFileSystem *owner, QObject *parent);
    /** 按 options 异步打开 backend 表示的路径；完成信号在本方法返回后交付。 */
    void start(std::shared_ptr<qvfs::Backend> backend, QVirtualOpenOptions options);
    /** 请求后端取消；打开成功但未领取的句柄仍由本任务管理。 */
    void requestCancel() override;

private:
    struct Private;             ///< 打开结果与默认所有者的私有状态，仅在 Qt 线程管理。
    std::shared_ptr<Private> d; ///< 打开结果、取消状态和默认所有者。
    friend class QVirtualFileSystem;
};
