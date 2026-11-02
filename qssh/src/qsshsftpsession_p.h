#pragma once

#include <asio.hpp>
#include <deque>
#include <functional>
#include <libssh/libssh.h>
#include <map>
#include <memory>
#include <optional>
#include <qssh/qsshsftpsession.h>
#include <vector>

class QSshSessionPrivate;

/** SFTP 的独立 Private；Qt 字段只在前端访问，其余状态只由原有 SSH worker 推进。 */
class QSshSftpSessionPrivate {
public:
    /** 支持的请求类型；文件数据读写可并行，其余操作作为顺序屏障。 */
    enum Kind {
        List,
        Stat,
        Lstat,
        Realpath,
        Readlink,
        Open,
        Close,
        Read,
        Write,
        Mkdir,
        Rmdir,
        Remove,
        Rename,
        Symlink,
        Chmod
    };

    /** 单个请求的结构化结果，内部传输直接在 worker 消费，不绕经 Qt。 */
    struct Result {
        QSshSftpSession::Error error = QSshSftpSession::NoError; ///< 远端或本地错误。
        QString message;                                         ///< 错误说明。
        QByteArray data;                                         ///< READ 返回的内容。
        QSshSftpSession::FileInfo info;                          ///< STAT 返回的属性。
        quint64 handle = 0;                                      ///< OPEN 返回的本地句柄编号。
        bool finished = false;                                   ///< 保证每个请求只完成一次。
    };

    /** 一项已接受请求；同时用来记录前端背压预算。 */
    struct Request {
        quint64 id = 0;                                              ///< 前端可见编号，同时用作成功打开的文件句柄编号。
        Kind kind = Stat;                                            ///< 操作类别。
        QString path;                                                ///< 首个路径参数。
        QString secondPath;                                          ///< 重命名目标或链接路径。
        quint64 handle = 0;                                          ///< 本实例文件句柄编号。
        quint64 offset = 0;                                          ///< 文件读写偏移。
        quint32 value = 0;                                           ///< 读长度、打开标志或 POSIX 权限。
        quint32 mode = 0644;                                         ///< 新建文件权限。
        QByteArray data;                                             ///< 待写入的数据，前端限制大小。
        std::function<void(Result)> completed;                       ///< 内部请求的 worker 完成回调，空值表示公开请求。
        std::shared_ptr<Result> result = std::make_shared<Result>(); ///< 协程副本共享结果与终结标志。
        std::function<bool()> cancelled;                             ///< 内部传输已取消时，尚未发送的请求直接结束。
        bool reserved = false;                                       ///< 已占用流水线字节预算。
    };

    /** 绑定 owner 与 session，注册到连接驱动器。 */
    QSshSftpSessionPrivate(QSshSftpSession *owner, QSshSession *session);
    /** 在 worker 同步取消资源，令迟到协程和回调失效。 */
    ~QSshSftpSessionPrivate();
    /** 将 task 投递到 worker，以独立存活令牌保护 this。 */
    void submit(std::function<void()> task) const;
    /** 将 task 排队到 QObject 所属线程，销毁时 Qt 自动移除。 */
    void notify(std::function<void()> task) const;
    /** 为前端 request 分配编号并检查队列预算，未接受时返回零。 */
    quint64 enqueue(Request request);
    /** 为内部 request 分配独立编号并排队，completed 在原 worker 上执行。 */
    void enqueueInternal(Request request, std::function<void(Result)> completed);
    /** 在字节窗口内启动非冲突读写，元数据和关闭操作等待此前请求完成。 */
    void pump();
    /** 执行并结束 request，结果由调用方或内部传输消费。 */
    asio::awaitable<void> doTask(Request request);
    /** 设置 worker 的 phase，排队更新 Qt 快照及相应生命周期信号。 */
    void setState(QSshSftpSession::State state);
    /** 以 configuration 启动唯一的 SFTP 协程。 */
    void start(QSshSftpSession::Options configuration);
    /** 顺序打开 channel 和协商 SFTP，再交给流水线调度器；网络等待均 co_await。 */
    asio::awaitable<void> doStart();
    /** 执行 request；成功返回 true，普通远端错误仅结束当前请求。 */
    asio::awaitable<bool> doRequest(Request request);
    /** 发送 type/payload 的 SFTP 请求，等待并返回匹配响应的 type+payload。 */
    asio::awaitable<QByteArray> doExchange(quint8 type, QByteArray payload);
    /** 等待协议进展或新请求；返回前先检查独立存活令牌。 */
    asio::awaitable<bool> doWaitForProgress();
    /** 由 SSH 统一驱动器推进有限字节的非阻塞读写，并唤醒协程。 */
    bool advance();
    /** 设置初次协商时限；打开后的每个请求有独立计时器。 */
    void setDeadline();
    /** 将 type/payload 编码后追加到待发送队列，保留部分写入进度。 */
    void sendPacket(quint8 type, const QByteArray &payload);
    /** 释放 request 的前端预算并排队报告结果，也用于关闭时清理尚未启动的请求。 */
    void finishRequest(const Request &request, bool success, QSshSftpSession::Error error, const QString &message);
    /** 关闭 channel，终止全部请求并可选报告子系统级 error/message。 */
    void shutdown(QSshSftpSession::Error error = QSshSftpSession::Cancelled, const QString &message = {});

    QSshSftpSession *q;                                           ///< 所属 QObject，生命周期覆盖本对象。
    QSshSessionPrivate *session;                                  ///< 所属连接的 Private，晚于本实例销毁。
    QSshSftpSession::State frontState = QSshSftpSession::Idle;    ///< Qt 状态快照。
    QSshSftpSession::Error frontError = QSshSftpSession::NoError; ///< Qt 终止错误快照。
    QString frontErrorText;                                       ///< Qt 终止错误说明。
    bool frontClosing = false;                                    ///< Qt 字段，close 后立即拒绝新请求。
    quint64 nextRequestId = 0;                                    ///< Qt 字段，单调递增的用户请求编号。
    qsizetype pendingBytes = 0;                                   ///< Qt 字段，未完成写入的总字节数。
    std::shared_ptr<bool> alive = std::make_shared<bool>(true);   ///< worker 存活令牌，协程持有副本。
    QSshSftpSession::State phase = QSshSftpSession::Idle;         ///< worker 协议状态。
    QSshSftpSession::Options options;                             ///< worker 时限配置。
    ssh_channel channel = nullptr;                                ///< 独立 SFTP channel，绝不与 shell 共享。
    asio::steady_timer progressEvent;                             ///< 无到期时间的唤醒事件，取消即唤醒。
    asio::steady_timer deadline;                                  ///< 打开或当前请求的整体时限。
    bool terminated = false;                                      ///< 资源已回收，禁止继续使用 channel。
    bool negotiating = true;                                      ///< 正在等待 VERSION，尚无 wire 请求编号。
    bool subsystemReady = false;                                  ///< SSH 子系统请求完成前不得读取 channel 数据。
    bool batchPending = false;          ///< 一批目录结果正在 Qt 线程消费，确认前不继续读取目录。
    quint32 wireId = 0;                 ///< SFTP v3 请求编号，与前端编号独立。
    QByteArray input;                   ///< 有界增量接收缓冲，处理分片及合并报文。
    QByteArray output;                  ///< 多个待发送报文，保留部分发送的数据。
    std::optional<QByteArray> response; ///< 仅用于初次 VERSION 协商。
    std::deque<Request> requests;       ///< 已接受且尚未启动的请求，按提交顺序执行。
    std::map<quint64, Request> running; ///< 已启动的请求，按用户编号索引。
    std::map<quint64, std::shared_ptr<asio::steady_timer>> requestTimers; ///< 各请求独立的整体时限。

    /** 一个在途协议交换的独立唤醒事件与响应。 */
    struct Exchange {
        explicit Exchange(asio::io_context &io) : event(io) {} ///< 在同一 worker 初始化事件。

        asio::steady_timer event;           ///< 等待本请求响应，非轮询。
        std::optional<QByteArray> response; ///< 通过 wire ID 分发的完整响应。
    };

    std::map<quint32, std::shared_ptr<Exchange>> exchanges; ///< 多个协议请求可以同时在途。
    quint64 nextInternalId = quint64(1) << 63;              ///< 与公开请求编号分离的内部序列。
    qint64 inFlightBytes = 0;                               ///< 在途内容及尚未由 Qt 消费的结果字节数。
    std::map<quint64, quint32> handleFlags;                 ///< 用于识别必须顺序执行的 Append 写入。
    std::vector<QSshSftpTransferPrivate *> transfers;       ///< worker 独占的高层传输，用于连接关闭通知。
    std::map<quint64, QByteArray> handles;                  ///< 本地文件句柄编号到远端不透明句柄，最多 128 项。
};
