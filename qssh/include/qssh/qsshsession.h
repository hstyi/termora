#pragma once

#include <QByteArray>
#include <QList>
#include <QObject>
#include <QScopedPointer>
#include <QStringList>

class QSshClient;
class QSshSessionPrivate;
class QSshChannelPrivate;
class QSshShellChannel;
class QSshExecChannel;
class QSshTunnel;
class QSshTunnelPrivate;
class QSshForwardStream;
class QSshSftpSession;
class QSshSftpSessionPrivate;

/** 一条 SSH 连接，通过 Client 创建；所有公共接口均在所属 Qt 线程调用。 */
class QSshSession : public QObject {
    Q_OBJECT
public:
    /** Closed 为终态；重新连接需要创建新 Session。 */
    enum State {
        Idle,           ///< 尚未连接。
        Connecting,     ///< 正在解析地址或建立 TCP 连接。
        Handshaking,    ///< 正在交换 SSH 协议和加密参数。
        VerifyingHost,  ///< 正在校验主机密钥，可能等待应用确认。
        Authenticating, ///< 正在认证，可能等待 OTP 等用户回答。
        Ready,          ///< 主机校验及全部认证完成，可以打开 Channel。
        Closing,        ///< 正在取消操作并回收资源。
        Closed          ///< 终态，连接不可再次启动。
    };
    Q_ENUM(State)

    /** Session 操作失败类别，具体原因见 errorString()。 */
    enum Error {
        NoError,              ///< 尚未发生错误。
        InvalidConfiguration, ///< 主机、用户、端口或时限等配置无效。
        NetworkError,         ///< 地址解析、TCP 连接或传输失败。
        HandshakeError,       ///< SSH 协商或协议事件初始化失败。
        HostKeyError,         ///< 主机密钥被拒绝或 known_hosts 操作失败。
        AuthenticationError,  ///< 凭据无效或没有可继续的认证方式。
        TimeoutError,         ///< 建连、主机确认或认证超过时限。
        Cancelled             ///< 应用取消了当前交互请求。
    };
    Q_ENUM(Error)

    /** 按配置顺序尝试；部分成功后仅继续服务器允许的方式。 */
    enum AuthenticationMethod {
        PublicKey,          ///< 使用 Options 中显式指定的私钥。
        Password,           ///< 使用 Options.password 进行密码认证。
        KeyboardInteractive ///< 按服务器问题进行多轮交互，支持 OTP。
    };
    Q_ENUM(AuthenticationMethod)

    /** 主机密钥与 known_hosts 的比较结果。 */
    enum HostKeyStatus {
        UnknownHost,     ///< 没有匹配的已知主机记录。
        ChangedHostKey,  ///< 同一算法的主机密钥发生变化。
        DifferentKeyType ///< 存在主机记录，但密钥算法不同。
    };
    Q_ENUM(HostKeyStatus)

    /** 当前主机密钥挑战的处理决定。 */
    enum HostKeyDecision {
        RejectHost,   ///< 拒绝密钥并关闭连接。
        AcceptOnce,   ///< 仅信任当前连接，不写入 known_hosts。
        AcceptAndSave ///< 接受并更新配置的 known_hosts 文件。
    };
    Q_ENUM(HostKeyDecision)

    /** 到 SSH 服务器之前使用的上游代理协议。 */
    enum ProxyType {
        NoProxy,          ///< 直接连接。
        HttpConnectProxy, ///< HTTP CONNECT，支持 Basic 代理认证。
        Socks5Proxy,      ///< SOCKS5 CONNECT，支持无认证或用户名／密码。
        Socks4Proxy       ///< SOCKS4／SOCKS4a，支持 user ID 及代理侧 DNS。
    };
    Q_ENUM(ProxyType)

    /** 上游代理配置；目标主机名由代理解析，HTTP 支持 Basic，SOCKS5 支持用户名／密码。 */
    struct ProxyOptions {
        ProxyType type = NoProxy; ///< NoProxy 直接连接 SSH 服务器。
        QString host;             ///< 代理服务器地址。
        quint16 port = 0;         ///< 代理服务器端口，启用代理时必须非零。
        QString username;         ///< HTTP Basic／SOCKS5 用户名，SOCKS4 的 user ID。
        QString password;         ///< HTTP Basic／SOCKS5 密码。
    };

    /** X11 转发配置；实际 cookie 仅发送给本地 X Server，远端使用随机替代 cookie。 */
    struct X11Options {
        bool enabled = false;            ///< 为此 Session 的 shell／exec 请求 X11 转发。
        QString display;                 ///< DISPLAY，例如 :0、localhost:10.0；空值读取环境变量。
        QByteArray authenticationCookie; ///< MIT-MAGIC-COOKIE-1 原始 16 字节；空值从 Xauthority 读取。
        QString authorityFile;           ///< Xauthority 文件；空值读取 XAUTHORITY 或 ~/.Xauthority。
        bool singleConnection = false;   ///< 仅允许一个来自服务器的 X11 连接。
    };

    /** 建连与认证配置；connectToHost() 按值保存。 */
    struct Options {
        QString host;                 ///< DNS 名称或 IP，不含端口。
        quint16 port = 22;            ///< TCP 端口。
        QString username;             ///< 登录用户，不隐式使用本机用户。
        QString password;             ///< Password 方法使用的密码，不用于猜测交互问题。
        QString privateKeyFile;       ///< 显式私钥路径；空值跳过 PublicKey。
        QString privateKeyPassphrase; ///< 私钥口令，需要交互时应在连接前获取。
        QString knownHostsFile;       ///< known_hosts 路径；空值使用 libssh 默认路径。
        QList<AuthenticationMethod> authenticationMethods = {PublicKey, Password, KeyboardInteractive}; ///< 尝试顺序。
        int connectTimeoutMs = 15000;         ///< DNS、TCP、握手合计时限，必须大于零。
        int authenticationTimeoutMs = 120000; ///< 主机确认和全部认证合计时限，必须大于零。
        int channelTimeoutMs = 15000;         ///< Channel 打开及主动关闭时限，必须大于零。
        ProxyOptions proxy;                   ///< 上游 HTTP CONNECT／SOCKS 代理。
        X11Options x11;                       ///< X11 显示及认证配置。
        bool agentForwarding = false;         ///< 为 shell／exec 请求 SSH Agent 转发。
        QString agentSocketPath;              ///< Unix agent socket 或 Windows 命名管道，空值使用系统默认。
    };

    /** 服务器提出的一项问题。 */
    struct AuthPrompt {
        QString text;      ///< 问题原文，可能是密码、OTP 或其他挑战。
        bool echo = false; ///< true 显示输入，false 使用隐藏输入。
    };

    /** 一轮 keyboard-interactive 挑战；零问题轮次仅通知，库自动提交空答案继续。 */
    struct AuthChallenge {
        quint64 requestId = 0;     ///< 本 Session 内唯一、只能回答一次的编号。
        QString name;              ///< 服务器提供的标题。
        QString instruction;       ///< 服务器提供的说明。
        QList<AuthPrompt> prompts; ///< 有序问题列表，答案须保持相同顺序。
    };

    /** 需要应用确认的服务器主机密钥。 */
    struct HostKeyChallenge {
        quint64 requestId = 0;              ///< 请求编号，与认证请求共用编号序列。
        QString host;                       ///< 原始连接主机名。
        quint16 port = 22;                  ///< 原始连接端口。
        QString keyType;                    ///< 密钥算法名称。
        QString fingerprint;                ///< SHA-256 指纹。
        HostKeyStatus status = UnknownHost; ///< known_hosts 比较结果。
    };

    /** 关闭连接并释放所属 Channel、SFTP 和隧道；由 Client 管理所有权。 */
    ~QSshSession() override;
    /** 返回前端已观察到的状态。 */
    State state() const;
    /** 返回最近一次失败类别。 */
    Error error() const;
    /** 返回最近一次失败说明，不包含密码或 OTP。 */
    QString errorString() const;
    /** 创建未打开的 Shell Channel，所有权归 Session。 */
    QSshShellChannel *createShellChannel();
    /** 创建未打开的 Exec Channel，所有权归 Session。 */
    QSshExecChannel *createExecChannel();
    /** 创建未启动的 local／remote／dynamic 隧道，所有权归 Session。 */
    QSshTunnel *createTunnel();
    /** 创建未打开的 SFTP 子系统，复用当前 SSH 连接，所有权归 Session。 */
    QSshSftpSession *createSftpSession();

public Q_SLOTS:
    /** 使用 options 异步连接，仅 Idle 接受调用；校验和认证完成后发 ready()。 */
    void connectToHost(const QSshSession::Options &options);
    /** 用 decision 回答 requestId 对应的主机确认；忽略过期或重复请求。 */
    void answerHostVerification(quint64 requestId, QSshSession::HostKeyDecision decision);
    /** 提交 answers；忽略数量错误、过期或重复的 requestId。 */
    void answerAuthentication(quint64 requestId, const QStringList &answers);
    /** 取消 requestId 对应的交互并关闭连接；空答案不表示取消。 */
    void cancelAuthentication(quint64 requestId);
    /** 异步关闭连接及所有 Channel、SFTP 和隧道，可以重复调用。 */
    void close();

Q_SIGNALS:
    /** 主机校验和全部认证已完成，可以打开 Channel、SFTP 和隧道。 */
    void ready();
    /** 生命周期变为 state，通知在所属 Qt 线程发出。 */
    void stateChanged(QSshSession::State state);
    /** 发生 error，具体说明见 errorString()。 */
    void errorOccurred(QSshSession::Error error);
    /** 要求应用异步确认 challenge 中的主机密钥。 */
    void hostVerificationRequired(const QSshSession::HostKeyChallenge &challenge);
    /** 要求应用异步回答 challenge 中的问题。 */
    void authenticationChallenge(const QSshSession::AuthChallenge &challenge);
    /** requestId 已回答、超时或失效，应关闭对应交互界面。 */
    void interactionFinished(quint64 requestId);
    /** 连接关闭且后台 SSH 资源已释放，仅发出一次。 */
    void closed();
    /** 某条转发连接失败，message 为原因；不关闭 SSH Session 或其他转发连接。 */
    void forwardingError(const QString &message);

private:
    /** 由 client 创建并管理生命周期。 */
    explicit QSshSession(QSshClient *client);
    QScopedPointer<QSshSessionPrivate> d_ptr; ///< 前端快照和 worker 独占的连接实现。
    Q_DECLARE_PRIVATE(QSshSession)
    Q_DISABLE_COPY(QSshSession)
    friend class QSshClient;
    friend class QSshChannelPrivate;
    friend class QSshTunnelPrivate;
    friend class QSshForwardStream;
    friend class QSshSftpSessionPrivate;
};

Q_DECLARE_METATYPE(QSshSession::State)
Q_DECLARE_METATYPE(QSshSession::Error)
Q_DECLARE_METATYPE(QSshSession::Options)
Q_DECLARE_METATYPE(QSshSession::HostKeyDecision)
Q_DECLARE_METATYPE(QSshSession::HostKeyChallenge)
Q_DECLARE_METATYPE(QSshSession::AuthChallenge)
