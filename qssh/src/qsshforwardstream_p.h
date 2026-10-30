#pragma once

#include <QByteArray>
#include <QString>
#include <asio.hpp>
#include <functional>
#include <libssh/libssh.h>
#include <memory>

class QSshSessionPrivate;

/** worker 内部的一条 socket↔SSH 转发流；协程持有 shared_ptr，避免组合 I/O 取消后的悬空引用。 */
class QSshForwardStream : public std::enable_shared_from_this<QSshForwardStream> {
public:
    /** 入站连接的本地目标类别。 */
    enum Kind {
        Tcp,  ///< 远端隧道的本地 TCP 目标。
        X11,  ///< 先验证 X11 cookie，再连接本地显示。
        Agent ///< 连接本地 Unix agent socket 或 Windows 命名管道。
    };

    /** 在 session 上创建连接，channel 非空表示服务器已经打开的入站 Channel。 */
    explicit QSshForwardStream(QSshSessionPrivate *session, ssh_channel channel = nullptr);
    /** 所有句柄应已在 worker 的 close() 中释放。 */
    ~QSshForwardStream();
    /** 用本地 accepted socket 打开到 host:port 的 SSH direct-tcpip，dynamic 时先解析 SOCKS4/5。 */
    void startOutgoing(asio::ip::tcp::socket accepted, const QString &host, quint16 port, bool dynamic);
    /** 将已接受的入站 SSH Channel 连接到 kind 指定的本地 host:port 或 path。 */
    void startIncoming(Kind kind, const QString &host, quint16 port, const QString &path = {});
    /** 推进 libssh 读写及半关闭，返回是否有实际进展。 */
    bool advance();
    /** 关闭全部资源；report 非空时向 Session 报告单连接错误。 */
    void close(const QString &report = {});
    /** 启动 operation 并持有本对象直到协程退出，异常仅关闭本条流。 */
    void launch(asio::awaitable<void> operation);
    /** 协商 SOCKS 并打开 direct-tcpip，成功后启动双向转接。 */
    asio::awaitable<void> doOutgoing();
    /** 连接本地 TCP／Unix socket／Windows agent 管道并启动转接。 */
    asio::awaitable<void> doIncoming();
    /** 解析本地 SOCKS4a／SOCKS5 CONNECT 请求，不消费后续负载。 */
    asio::awaitable<bool> doSocks();
    /** 校验 X11 setup 的替代 cookie，换成本地真实 cookie。 */
    asio::awaitable<bool> doX11Authentication();
    /** 从本地 socket 读到发送队列，队列满时挂起等待 SSH 窗口。 */
    asio::awaitable<void> doReadSocket();
    /** 将接收队列写到本地 socket，排空后再处理远端 EOF。 */
    asio::awaitable<void> doWriteSocket();
    /** 从本地 socket 读取恰好 count 字节，用于 SOCKS 协商。 */
    asio::awaitable<QByteArray> doReadExactly(int count);
    /** 完整发送 data 到本地 socket，用于 SOCKS 应答。 */
    asio::awaitable<void> doWrite(QByteArray data);
    /** 等待 event 对应的内部状态变化；不是周期轮询。 */
    asio::awaitable<void> doWait(asio::steady_timer &event);
    /** 启动有时限的连接或代理协商。 */
    void startDeadline();
    /** 连接完成后启动 socket 读取，恢复 Session 的统一驱动。 */
    void beginBridge();

    QSshSessionPrivate *session;      ///< 仅在 !closed 时访问，Session 关闭先调用本流 close()。
    asio::ip::tcp::socket socket;     ///< 本地 TCP socket；协程持有本对象以覆盖 I/O 生命周期。
    asio::ip::tcp::resolver resolver; ///< 本地目标的地址解析器。
#ifdef ASIO_HAS_LOCAL_SOCKETS
    std::unique_ptr<asio::local::stream_protocol::socket> localSocket; ///< Unix X11／Agent endpoint。
#endif
#ifdef ASIO_HAS_WINDOWS_STREAM_HANDLE
    std::unique_ptr<asio::windows::stream_handle> pipe; ///< Windows OpenSSH Agent 命名管道。
#endif
    asio::steady_timer deadline;                  ///< 打开通道、SOCKS 和本地连接的时限。
    asio::steady_timer openEvent;                 ///< direct-tcpip 响应唤醒事件。
    asio::steady_timer inputEvent;                ///< 发送队列释放容量后的唤醒事件。
    asio::steady_timer outputEvent;               ///< X11 setup 接收进展事件。
    ssh_channel channel = nullptr;                ///< worker 独占的 SSH Channel。
    QString host;                                 ///< 本地或远端 TCP 目标主机名。
    quint16 port = 0;                             ///< 目标 TCP 端口。
    QString path;                                 ///< Unix socket 或 Windows 命名管道路径。
    QString sourceHost;                           ///< direct-tcpip 发起地址。
    quint16 sourcePort = 0;                       ///< direct-tcpip 发起端口。
    Kind kind = Tcp;                              ///< 入站连接使用的本地 endpoint 类型。
    bool dynamic = false;                         ///< 本地连接需要 SOCKS 协商。
    int socksVersion = 0;                         ///< SOCKS 应答使用 4 或 5。
    bool closed = false;                          ///< 所有协程恢复后必须先检查的终态标志。
    bool opening = false;                         ///< 正在等待 direct-tcpip 打开结果。
    bool openSucceeded = false;                   ///< direct-tcpip 是否被服务器接受。
    bool bridgeReady = false;                     ///< 本地目标已连接，允许写出 SSH 数据。
    bool writing = false;                         ///< 已有本地写协程，禁止重叠写。
    bool localEof = false;                        ///< 本地输入已 EOF，发送队列排空后发送 SSH EOF。
    bool eofSent = false;                         ///< 已发送 SSH EOF。
    bool remoteEof = false;                       ///< SSH 接收缓冲已排空并收到 EOF。
    bool socketSendClosed = false;                ///< 已将远端 EOF 传播到本地发送方向。
    QByteArray toSsh;                             ///< 有界本地→SSH 队列。
    QByteArray toSocket;                          ///< 有界 SSH→本地队列。
    std::function<void(const QString &)> onError; ///< 所属隧道的错误通知，回调须检查自身存活令牌。
};
