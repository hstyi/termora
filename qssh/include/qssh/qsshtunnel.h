#pragma once

#include <QObject>
#include <QScopedPointer>

class QSshSession;
class QSshTunnelPrivate;

/**
 * SSH TCP 隧道；通过 Session 创建，全部公共方法在所属 Qt 线程调用。
 * Dynamic 提供无认证的 SOCKS4a／SOCKS5 CONNECT 服务，不支持 UDP/BIND；默认只监听回环地址。
 * 单 Session 最多保留 128 条转发流，每条流每方向队列上限为 256 KiB。
 */
class QSshTunnel : public QObject {
    Q_OBJECT
public:
    /** 转发方向，分别对应 ssh -L、-R 和 -D。 */
    enum Type {
        Local,  ///< 本地监听，目标连接由 SSH 服务器建立。
        Remote, ///< 服务器监听，目标连接由 SSH 客户端建立。
        Dynamic ///< 本地 SOCKS4a／SOCKS5，根据每次 CONNECT 请求选择远端目标。
    };
    Q_ENUM(Type)

    /** Closed 为终态，重新监听需要创建新对象。 */
    enum State {
        Idle,      ///< 尚未启动。
        Opening,   ///< 正在绑定本地端口或请求服务器监听。
        Listening, ///< 可以接受转发连接，boundPort() 返回实际端口。
        Closing,   ///< 正在撤销监听并关闭连接。
        Closed     ///< 终态，不可再次启动。
    };
    Q_ENUM(State)

    /** 隧道错误；ConnectionError 仅影响当前接入连接。 */
    enum Error {
        NoError,              ///< 尚未发生错误。
        InvalidConfiguration, ///< 配置无效，或 Session 未就绪。
        ListenError,          ///< 本地监听、绑定或 accept 失败。
        RequestRejected,      ///< 服务器拒绝创建或撤销监听。
        ConnectionError,      ///< 一条转发连接失败，监听器继续工作。
        TimeoutError          ///< 远端监听操作超过时限。
    };
    Q_ENUM(Error)

    /** 隧道监听及目标配置。 */
    struct Options {
        Type type = Local;                                ///< 转发模式。
        QString listenHost = QStringLiteral("127.0.0.1"); ///< Local/Dynamic 必须是 IP，Remote 为服务器绑定地址。
        quint16 listenPort = 0;                           ///< 零表示由操作系统或 SSH 服务器分配端口。
        QString destinationHost;                          ///< Local 在服务器侧连接此主机，Remote 在客户端侧连接此主机。
        quint16 destinationPort = 0;                      ///< Local/Remote 的目标端口，Dynamic 从每次 SOCKS 请求获取。
    };

    /** 取消监听并关闭所属连接；Session 必须仍然存活。 */
    ~QSshTunnel() override;
    /** 返回 Qt 线程观察到的状态。 */
    State state() const;
    /** 返回最近一次失败类别。 */
    Error error() const;
    /** 返回最近一次失败说明。 */
    QString errorString() const;
    /** 返回实际监听端口，在 listening() 后有效。 */
    quint16 boundPort() const;
    /** 使用 options 异步开启隧道，仅 Idle 接受调用；Session 必须已 Ready。 */
    void open(const QSshTunnel::Options &options);
    /** 停止接受新连接，关闭已建立连接，并撤销远端监听；可重复调用。 */
    void close();
Q_SIGNALS:
    /** 监听建立成功，可以读取 boundPort()。 */
    void listening();
    /** 生命周期变为 state。 */
    void stateChanged(QSshTunnel::State state);
    /** 发生 error，详情见 errorString()。 */
    void errorOccurred(QSshTunnel::Error error);
    /** 监听已撤销且所属转发连接已关闭。 */
    void closed();

private:
    /** 由 session 创建并管理生命周期。 */
    explicit QSshTunnel(QSshSession *session);
    QScopedPointer<QSshTunnelPrivate> d_ptr; ///< 隐藏 Asio 监听器、远端请求和连接集合。
    Q_DECLARE_PRIVATE(QSshTunnel)
    Q_DISABLE_COPY(QSshTunnel)
    friend class QSshSession;
};

Q_DECLARE_METATYPE(QSshTunnel::State)
Q_DECLARE_METATYPE(QSshTunnel::Error)
