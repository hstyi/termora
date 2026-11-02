#pragma once

#include "qsshsftpsession_p.h"

#include <QFileDevice>
#include <QPointer>
#include <atomic>
#include <map>
#include <mutex>
#include <set>

/** 外部 QIODevice 的单线程桥接器，只在设备所属 Qt 线程执行实际读写。 */
class QSshSftpDeviceBridge : public QObject {
public:
    /** 一次设备操作的结果；eof 仅由非顺序设备末尾或显式结束输入确定。 */
    struct Result {
        QByteArray data;   ///< 读取的内容。
        QString error;     ///< 空字符串表示成功。
        qint64 total = -1; ///< 上传源剩余大小，顺序设备未知。
        bool eof = false;  ///< 输入确实结束，临时无数据不算 EOF。
    };

    using Callback = std::function<void(Result)>; ///< 结果始终投递回原 Asio worker。
    /** 借用 device，在 executor 上交付结果，不取得设备所有权。 */
    QSshSftpDeviceBridge(QIODevice *device, asio::any_io_executor executor);
    /** 异步验证设备方向并连接就绪和销毁信号。 */
    void prepare(bool upload, Callback callback);
    /** 异步读取最多 size 字节；顺序源无数据时保留请求等待 readyRead。 */
    void read(qint64 size, Callback callback);
    /** 异步完整写出 data，处理短写及 bytesWritten，设备缓冲排空后才完成。 */
    void write(QByteArray data, Callback callback);
    /** 声明顺序输入结束，继续排空设备已有内容。 */
    void finishInput();
    /** 跨线程停止后续设备操作，并在设备线程销毁桥接器。 */
    void stop();

private:
    /** 尝试完成挂起读取，不把顺序源的空读当作 EOF。 */
    void drainRead();
    /** 尝试完成挂起写入，等待设备解除背压。 */
    void drainWrite();
    /** 将 result 和 callback 移交 worker。 */
    void deliver(Callback callback, Result result);
    /** 设备错误时结束目前挂起的读写。 */
    void fail(const QString &message);
    QPointer<QIODevice> device;       ///< 只在设备线程解引用，不关闭或删除。
    asio::any_io_executor executor;   ///< 原有唯一 SSH worker。
    std::mutex dispatchMutex;         ///< stop 与向 worker 投递互斥，防止 Client 析构后迟到投递。
    std::atomic_bool stopped = false; ///< stop 可跨线程立即阻止排队操作。
    bool ended = false;               ///< 输入结束标志。
    bool writing = false;             ///< 防止同步 bytesWritten 导致重入。
    qint64 readSize = 0;              ///< 当前挂起读取长度。
    QByteArray output;                ///< 当前待写内容。
    qsizetype outputOffset = 0;       ///< 已接受的部分写入长度。
    Callback reader;                  ///< 当前读取完成通知。
    Callback writer;                  ///< 当前写入完成通知。
};

/** 传输对象的 Qt Private；除 front 字段外，状态由原有 worker 独占。 */
class QSshSftpTransferPrivate {
public:
    using RemoteResult = QSshSftpSessionPrivate::Result; ///< 内部 SFTP 请求结果。
    using Request = QSshSftpSessionPrivate::Request;     ///< 内部 SFTP 请求。
    /** 绑定 owner、sftp、方向和本地入口；公开对象返回后才异步启动。 */
    QSshSftpTransferPrivate(QSshSftpTransfer *owner, QSshSftpSession *sftp, bool upload, QString remote, QString local,
                            QIODevice *device, QSshSftpTransfer::Options options);
    /** 同步取消 worker 状态，并阻止设备线程中尚未执行的操作。 */
    ~QSshSftpTransferPrivate();
    /** 安全投递 worker 任务，以独立令牌保护生命周期。 */
    void submit(std::function<void()> task);
    /** 启动 operation，统一接收协程异常。 */
    void launch(asio::awaitable<void> operation);
    /** 准备源／目标和远端句柄，持续补充流水线，最后关闭并提交文件。 */
    asio::awaitable<void> doStart();
    /** 等待对应 request 的响应，不经 Qt 信号转发内容。 */
    asio::awaitable<RemoteResult> doRemote(Request request);
    /** 等待桥接器的 prepare/read/write 操作，kind 分别为 0/1/2。 */
    asio::awaitable<QSshSftpDeviceBridge::Result> doDevice(int kind, QByteArray data = {});
    /** 读取 offset 开始的 length 字节，短读继续补齐，结果按偏移等待写出。 */
    asio::awaitable<void> doRead(qint64 offset, qint64 length);
    /** 上传 offset 对应的 data，只在服务器确认后累计进度。 */
    asio::awaitable<void> doWrite(qint64 offset, QByteArray data);
    /** 等待网络、设备或取消引起的一次进展，无周期轮询。 */
    asio::awaitable<bool> doWait();
    /** 更新整体空闲时限，只有实际进展才重置。 */
    void touch();
    /** 更新 worker 状态并排队交付 Qt 快照。 */
    void setState(QSshSftpTransfer::State state);
    /** 合并尚未交付的进度，避免 UI 暂停时堆积逐块通知。 */
    void reportProgress(bool force = false);
    /** 进入终态，回收本地资源，并为尚在途的远端操作安排后续清理。 */
    void finish(QSshSftpTransfer::State state, QSshSftpTransfer::Error error, const QString &message);

    QSshSftpTransfer *q;                                            ///< 所属 Qt 对象。
    QSshSftpSessionPrivate *sftp;                                   ///< 晚于传输销毁的 SFTP 实现。
    QSshSftpTransfer::State frontState = QSshSftpTransfer::Pending; ///< Qt 生命周期快照。
    QSshSftpTransfer::Error frontError = QSshSftpTransfer::NoError; ///< Qt 错误快照。
    QString frontErrorText;                                         ///< Qt 错误说明。
    qint64 frontCompleted = 0, frontTotal = -1;                     ///< Qt 字节进度快照。
    std::shared_ptr<bool> alive = std::make_shared<bool>(true);     ///< worker 存活令牌。
    bool upload;                                                    ///< true 上传，false 下载。
    QString remotePath, localPath;                                  ///< 远端及可选本地文件路径。
    QSshSftpTransfer::Options options;                              ///< 本传输的行为和缓冲配置。
    QSshSftpDeviceBridge *bridge = nullptr;                         ///< 可选设备线程桥接器，stop 后自行释放。
    std::unique_ptr<QFileDevice> file;                              ///< worker 创建并独占的本地文件。
    asio::steady_timer event, idle;                                 ///< 无到期唤醒事件和空闲时限。
    bool stopped = false, sourceEnded = false;                      ///< 终结和上传 EOF 状态。
    bool downloadSizeKnown = false;         ///< true 时提前 EOF 是源文件变化，未知大小则由最早 EOF 确定长度。
    quint64 handle = 0;                     ///< 远端文件句柄。
    qint64 total = -1, completed = 0;       ///< worker 总量和已完成内容字节数。
    qint64 nextOffset = 0, writeOffset = 0; ///< 下一块远端偏移和下载顺序写出位置。
    qint64 reserved = 0;                    ///< 在途及等待写出的下载／上传内容预算。
    int pending = 0;                        ///< 已启动但尚未完成的内容块数，不作为请求上限。
    std::map<qint64, QByteArray> ready;     ///< 乱序下载结果，消费前仍占用 reserved。

    /** 一个协程等待对象，独立于 Private 存活。 */
    struct Wait {
        explicit Wait(asio::io_context &io)
            : event(std::make_unique<asio::steady_timer>(io)) {} ///< 在同一 worker 初始化事件。

        std::unique_ptr<asio::steady_timer> event; ///< 取消即可恢复；终止时在 worker 释放，桥接回调可更晚销毁。
        bool done = false, abandoned = false;      ///< 已返回结果或调用方取消等待。
        RemoteResult remote;                       ///< SFTP 结果。
        QSshSftpDeviceBridge::Result device;       ///< 本地设备结果。
    };

    std::set<std::shared_ptr<Wait>> waits; ///< 取消和析构时唤醒所有并行等待。

    /** 可跨 worker 与 Qt 合并读取的最新进度。 */
    struct Progress {
        std::mutex mutex;                 ///< 保护下面的快照。
        qint64 completed = 0, total = -1; ///< 最新字节进度。
        bool queued = false;              ///< 最多一个待交付进度通知。
    };

    std::shared_ptr<Progress> progress = std::make_shared<Progress>(); ///< 独立的合并通知快照。
};
