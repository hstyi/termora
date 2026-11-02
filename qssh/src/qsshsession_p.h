#pragma once

#include <asio.hpp>
#include <deque>
#include <functional>
#include <libssh/callbacks.h>
#include <libssh/libssh.h>
#include <memory>
#include <qssh/qsshsession.h>
#include <vector>

class QSshClientPrivate;
class QSshTunnelPrivate;
class QSshForwardStream;
class QSshSftpSessionPrivate;

/** Session 的 Qt 私有实现；除明确标注的前端字段外，状态仅由 worker 访问。 */
class QSshSessionPrivate {
public:
    static constexpr size_t MaximumForwardedConnections = 128; ///< 单 Session 的转发流上限，限制慢连接资源占用。
    /** 绑定 owner 与 client，初始化尚未使用的网络资源。 */
    QSshSessionPrivate(QSshSession *owner, QSshClient *client);
    /** 同步撤销回调并回收后台连接。 */
    ~QSshSessionPrivate();
    /** 把 task 安全投递到 worker；生命周期令牌使已排队任务在析构后失效。 */
    void submit(std::function<void()> task) const;
    /** 将 task 排队到 q 所属 Qt 线程，QObject 销毁时 Qt 自动移除尚未执行的通知。 */
    void notify(std::function<void()> task) const;
    /** 保存 configuration，并在唯一 worker 上启动连接协程。 */
    void start(QSshSession::Options configuration);
    /** 启动 operation；统一接收协程异常，并在析构后忽略迟到的完成通知。 */
    void launch(asio::awaitable<void> operation);
    /** 顺序执行连接、握手、主机校验和认证，每个异步阶段均通过 co_await 挂起。 */
    asio::awaitable<void> doStart();
    /** 依次解析地址并建立 TCP 连接，失败时关闭 Session。 */
    asio::awaitable<void> doConnect();
    /** 在已连接的代理 socket 上协商 HTTP CONNECT、SOCKS4a 或 SOCKS5。 */
    asio::awaitable<void> doProxyHandshake();
    /** 向代理完整发送 data，取消后不再访问 Private。 */
    asio::awaitable<bool> doProxyWrite(QByteArray data);
    /** 从代理读取恰好 count 字节，避免误消费随后到达的 SSH banner。 */
    asio::awaitable<QByteArray> doProxyRead(int count);
    /** 准备 X11 cookie、显示地址及入站转发回调；失败时返回 false。 */
    bool initializeForwarding();
    /** 推进串行远端监听请求和全部转发流，返回是否仍有可立即推进的工作。 */
    bool advanceForwarding();
    /** 在 worker 上关闭隧道和转发流，必须先于 ssh_free()。 */
    void shutdownForwarding();
    /** 串行提交远端 listen/cancel 请求；completed 接收 libssh 结果和实际监听端口。 */
    void requestRemote(bool cancel, const QString &address, quint16 port, std::function<void(int, int)> completed);
    /** 转发单条连接的 error 给 Qt 线程，不终止 Session。 */
    void reportForwardingError(const QString &error);
    /** 非阻塞重试 SSH 握手，等待统一 socket 驱动器通知协议进展。 */
    asio::awaitable<void> doHandshake();
    /** 校验服务器主机密钥，必要时挂起等待应用确认。 */
    asio::awaitable<void> doVerifyHost();
    /** 顺序推进认证，网络等待和用户回答均挂起协程；立即完成的步骤显式让出执行权。 */
    asio::awaitable<void> doAuthenticate();
    /** 等待一次协议进展或交互回答；关闭／析构后返回 false，不轮询定时器。 */
    asio::awaitable<bool> doWaitForProgress();
    /** 等待 type 指定的 socket 就绪状态，然后请求统一驱动；不读取 SSH 原始数据。 */
    asio::awaitable<void> doWaitSocket(asio::ip::tcp::socket::wait_type type);
    /** 将已连接的 socket 交给非阻塞 libssh，成功返回 true。 */
    bool initializeSession();
    /** 合并多次驱动请求，在下一次 worker 调度时继续推进。 */
    void schedule();
    /** 统一处理协议事件和 Channel，唤醒连接协程；不代替协程推进握手或认证。 */
    void drive();
    /** 根据 libssh 需求注册 socket 就绪等待。 */
    void armWaits();
    /** 设置后台阶段 value，并异步更新前端快照。 */
    void setState(QSshSession::State value);
    /** 设置时限 milliseconds，到期终止连接；替换上一阶段的时限。 */
    void setDeadline(int milliseconds);
    /** 验证服务器主机密钥，必要时向应用发出确认请求。 */
    void verifyHost();
    /** 根据 decision 处理 requestId 对应的主机确认。 */
    void answerHost(quint64 requestId, QSshSession::HostKeyDecision decision);
    /** 将 answers 写入 requestId 对应的交互轮次并恢复认证。 */
    void answerAuth(quint64 requestId, const QStringList &answers);
    /** 令当前交互请求失效，通知 UI 清理对应界面。 */
    void finishInteraction();
    /** 推进一次认证操作，返回是否能够立即继续下一阶段。 */
    bool advanceAuthentication();
    /** 处理认证返回值 result，包括部分成功和多轮问题。 */
    bool authResult(int result);
    /** 报告 error/message 并关闭连接，message 不应包含凭据。 */
    void fail(QSshSession::Error error, const QString &message);
    /** 终止所属 Channel 并按依赖顺序释放资源；通知前端连接关闭。 */
    void shutdown();
    /** 返回当前 libssh 错误文本的独立副本。 */
    QString sshError() const;

    QSshSession *q;                                       ///< 生命周期覆盖私有实现的前端对象。
    QSshClientPrivate *client;                            ///< Client 的私有实现，晚于本连接销毁。
    QSshSession::State frontState = QSshSession::Idle;    ///< Qt 线程独占的状态快照。
    QSshSession::Error frontError = QSshSession::NoError; ///< Qt 线程独占的错误类别。
    QString frontErrorText;                               ///< Qt 线程独占的错误说明。
    bool frontClosing = false;                            ///< Qt 线程独占，抑制 close() 后迟到的中间状态。
    bool started = false;                                 ///< Qt 线程独占，保证只启动一次。

    std::shared_ptr<bool> alive = std::make_shared<bool>(true); ///< worker 独占的存活标志，取消回调持有副本。
    asio::ip::tcp::resolver resolver;                        ///< 异步解析器；平台解析实现可能使用 Asio 内部辅助线程。
    asio::ip::tcp::socket socket;                            ///< TCP 句柄唯一所有者；交给 libssh 后仅做就绪等待。
    asio::steady_timer deadline;                             ///< 建连或认证阶段的整体时限。
    asio::steady_timer progressEvent;                        ///< 无到期时间的协程唤醒事件，以 cancel_one() 通知进展。
    QSshSession::Options options;                            ///< worker 独占的连接配置。
    QSshSession::State phase = QSshSession::Idle;            ///< worker 独占的协议阶段。
    ssh_session handle = nullptr;                            ///< worker 独占的 libssh Session。
    ssh_event event = nullptr;                               ///< 当前 Session 的零超时协议事件驱动器。
    ssh_key privateKey = nullptr;                            ///< 当前公钥认证使用的解密后私钥。
    bool terminated = false;                                 ///< shutdown() 已执行，后续驱动均忽略。
    bool scheduled = false;                                  ///< 已排队驱动，合并重复唤醒。
    bool readWaiting = false;                                ///< 已注册一次 socket 可读等待。
    bool writeWaiting = false;                               ///< 已注册一次 socket 可写等待。
    bool authProbed = false;                                 ///< none 认证探测已经完成。
    int authIndex = -1;                                      ///< 当前配置认证方式索引，-1 表示待选择。
    int triedMethods = 0;                                    ///< 当前认证因素已经尝试的方法位图。
    int authRounds = 0;                                      ///< 限制异常服务器无限发送挑战或认证因素。
    quint64 nextRequestId = 0;                               ///< Session 生命周期内递增的交互请求序号。
    quint64 pendingRequest = 0;                              ///< 正在等待的交互请求，零表示无请求。
    int promptCount = -1;                                    ///< -1 表示主机确认，否则表示认证问题数量。
    std::vector<QSshChannelPrivate *> channels;              ///< worker 独占的 Channel 列表，析构通过屏障移除。
    std::vector<QSshTunnelPrivate *> tunnels;                ///< worker 独占的隧道列表。
    std::vector<QSshSftpSessionPrivate *> sftpSessions;      ///< worker 独占的独立 SFTP 子系统列表。
    std::vector<std::shared_ptr<QSshForwardStream>> streams; ///< 转发流的存活集合，关闭后由驱动器移除。

    /** 单个串行远端监听操作，独立于前端隧道对象生命周期。 */
    struct RemoteRequest {
        bool cancel;                             ///< true 撤销监听，false 请求监听。
        QByteArray address;                      ///< UTF-8 绑定地址，重试期间保持不变。
        quint16 port;                            ///< 请求端口或待撤销的实际端口。
        std::function<void(int, int)> completed; ///< 操作完成回调。
    };

    std::deque<RemoteRequest> remoteRequests; ///< libssh 同时只允许一个未完成的全局请求。

    /** 来自服务器的 forwarded-tcpip，等待远端监听响应更新端口后再路由。 */
    struct IncomingForward {
        ssh_channel channel; ///< 已接受的 SSH Channel，必须显式释放。
        QString address;     ///< 服务器实际接入的监听地址。
        quint16 port;        ///< 服务器实际接入的监听端口。
    };

    std::vector<IncomingForward> incomingForwards; ///< 回调仅入队，驱动器负责连接本地目标。
    ssh_callbacks_struct sessionCallbacks = {};    ///< 入站 X11、Agent 和远端 TCP 转发回调。
    QByteArray x11FakeCookie;                      ///< 仅发送给远端的随机 16 字节 cookie。
    bool x11Requested = false;                     ///< 已有 Channel 成功请求 X11 转发。
    bool x11Accepted = false;                      ///< singleConnection 模式已经接受过一个连接。
    bool agentRequested = false;                   ///< 已有 Channel 请求 Agent 转发。
    QString x11SocketPath;                         ///< Unix X Server socket 路径，非空时优先于 TCP。
    QString x11Host;                               ///< TCP X Server 地址。
    quint16 x11Port = 0;                           ///< TCP X Server 端口。
    int x11Screen = 0;                             ///< X11 DISPLAY screen 编号。
};
