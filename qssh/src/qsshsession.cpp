#include "qsshchannel_p.h"
#include "qsshclient_p.h"
#include "qsshsession_p.h"
#include "qsshsftpsession_p.h"

#include <QFile>
#include <QPointer>
#include <QThread>
#include <algorithm>
#include <qssh/qsshexecchannel.h>
#include <qssh/qsshsftpsession.h>
#include <qssh/qsshshellchannel.h>
#include <qssh/qsshtunnel.h>

namespace {
    /** 在首次调度时检查 token，再启动 operation；即使私有对象已销毁也能安全丢弃未启动的协程帧。 */
    asio::awaitable<void> runIfAlive(const std::shared_ptr<bool> token, asio::awaitable<void> operation) {
        if (*token) {
            co_await std::move(operation);
        }
    }

    /** 将 method 转为 libssh 使用的认证方式位。 */
    int methodFlag(const QSshSession::AuthenticationMethod method) {
        switch (method) {
            case QSshSession::PublicKey:
                return SSH_AUTH_METHOD_PUBLICKEY;
            case QSshSession::Password:
                return SSH_AUTH_METHOD_PASSWORD;
            case QSshSession::KeyboardInteractive:
                return SSH_AUTH_METHOD_INTERACTIVE;
        }
        return 0;
    }
} // namespace

QSshSessionPrivate::QSshSessionPrivate(QSshSession *owner, QSshClient *client)
    : q(owner),
      client(client->d_func()),
      resolver(this->client->io),
      socket(this->client->io),
      deadline(this->client->io),
      progressEvent(this->client->io) {}

QSshSessionPrivate::~QSshSessionPrivate() {
    client->synchronize([this] {
        shutdown();
        // 已排队及取消后才到达的回调先检查令牌，不再解引用 this。
        *alive = false;
    });
}

void QSshSessionPrivate::submit(std::function<void()> task) const {
    asio::post(client->io, [token = alive, task = std::move(task)] {
        if (*token) {
            task();
        }
    });
}

void QSshSessionPrivate::notify(std::function<void()> task) const {
    QMetaObject::invokeMethod(q, std::move(task), Qt::QueuedConnection);
}

void QSshSessionPrivate::setState(QSshSession::State value) {
    phase = value;
    notify([this, value] {
        if (frontState == value || (frontClosing && value != QSshSession::Closing && value != QSshSession::Closed)) {
            return;
        }
        frontState = value;
        const QPointer guard(q);
        Q_EMIT q->stateChanged(value);
        if (!guard) {
            return;
        }
        if (value == QSshSession::Ready && !frontClosing) {
            Q_EMIT q->ready();
        } else if (value == QSshSession::Closed) {
            Q_EMIT q->closed();
        }
    });
}

void QSshSessionPrivate::setDeadline(const int milliseconds) {
    deadline.expires_after(std::chrono::milliseconds(milliseconds));
    deadline.async_wait([this, token = alive](const asio::error_code &error) {
        if (*token && !error && !terminated) {
            fail(QSshSession::TimeoutError, QStringLiteral("SSH operation timed out"));
        }
    });
}

void QSshSessionPrivate::start(QSshSession::Options configuration) {
    if (terminated) {
        return;
    }
    options = std::move(configuration);
    phase = QSshSession::Connecting;
    setDeadline(options.connectTimeoutMs);
    launch(doStart());
}

void QSshSessionPrivate::launch(asio::awaitable<void> operation) {
    asio::co_spawn(client->io, runIfAlive(alive, std::move(operation)),
                   [this, token = alive](const std::exception_ptr &error) {
                       if (!*token || terminated || !error) {
                           return;
                       }
                       try {
                           std::rethrow_exception(error);
                       } catch (const std::exception &exception) {
                           fail(QSshSession::NetworkError, QString::fromUtf8(exception.what()));
                       } catch (...) {
                           fail(QSshSession::NetworkError, QStringLiteral("Unexpected SSH coroutine failure"));
                       }
                   });
}

asio::awaitable<void> QSshSessionPrivate::doStart() {
    const auto token = alive;
    if (terminated) {
        co_return;
    }
    co_await doConnect();
    // 协程帧可以晚于 QObject 销毁；每次恢复先检查独立存活令牌，再访问 this。
    if (!*token || terminated) {
        co_return;
    }
    if (options.proxy.type != QSshSession::NoProxy) {
        co_await doProxyHandshake();
        if (!*token || terminated) {
            co_return;
        }
    }
    if (!initializeSession() || !initializeForwarding()) {
        co_return;
    }
    co_await doHandshake();
    if (!*token || terminated) {
        co_return;
    }
    setDeadline(options.authenticationTimeoutMs);
    co_await doVerifyHost();
    if (!*token || terminated) {
        co_return;
    }
    co_await doAuthenticate();
}

asio::awaitable<void> QSshSessionPrivate::doConnect() {
    const auto token = alive;
    asio::error_code error;
    const bool proxied = options.proxy.type != QSshSession::NoProxy;
    const auto addresses = co_await resolver.async_resolve((proxied ? options.proxy.host : options.host).toStdString(),
                                                           std::to_string(proxied ? options.proxy.port : options.port),
                                                           asio::redirect_error(asio::use_awaitable, error));
    if (!*token || terminated) {
        co_return;
    }
    if (error) {
        fail(QSshSession::NetworkError, QString::fromStdString(error.message()));
        co_return;
    }
    error = asio::error::host_not_found;
    for (const auto &address : addresses) {
        asio::error_code ignored;
        ignored = socket.close(ignored);
        ignored = socket.open(address.endpoint().protocol(), error);
        if (error) {
            continue;
        }
        // 逐地址等待原生连接操作，避免组合 async_connect 在取消后继续持有 Private 中 socket 的引用。
        co_await socket.async_connect(address.endpoint(), asio::redirect_error(asio::use_awaitable, error));
        if (!*token || terminated) {
            co_return;
        }
        if (!error) {
            co_return;
        }
    }
    fail(QSshSession::NetworkError, QString::fromStdString(error.message()));
}

bool QSshSessionPrivate::initializeSession() {
    // 交接后 Asio 只等待就绪，SSH 读写完全由非阻塞 libssh 完成。
    asio::error_code socketError;
    socketError = socket.native_non_blocking(true, socketError);
    if (socketError) {
        fail(QSshSession::NetworkError, QString::fromStdString(socketError.message()));
        return false;
    }
    handle = ssh_new();
    if (!handle) {
        fail(QSshSession::HandshakeError, QStringLiteral("Unable to allocate SSH session"));
        return false;
    }
    const auto host = options.host.toUtf8();
    const auto user = options.username.toUtf8();
    const auto knownHosts = QFile::encodeName(options.knownHostsFile);
    const socket_t fd = socket.native_handle();
    const unsigned int port = options.port;
    constexpr bool processConfig = false;
    // 不读取 ~/.ssh/config，避免隐式 ProxyCommand 或配置覆盖显式连接参数。
    if (ssh_options_set(handle, SSH_OPTIONS_PROCESS_CONFIG, &processConfig) != SSH_OK ||
        ssh_options_set(handle, SSH_OPTIONS_HOST, host.constData()) != SSH_OK ||
        ssh_options_set(handle, SSH_OPTIONS_PORT, &port) != SSH_OK ||
        ssh_options_set(handle, SSH_OPTIONS_USER, user.constData()) != SSH_OK ||
        ssh_options_set(handle, SSH_OPTIONS_FD, &fd) != SSH_OK ||
        (!knownHosts.isEmpty() && ssh_options_set(handle, SSH_OPTIONS_KNOWNHOSTS, knownHosts.constData()) != SSH_OK)) {
        fail(QSshSession::HandshakeError, sshError());
        return false;
    }
    ssh_set_blocking(handle, 0);
    return true;
}

asio::awaitable<void> QSshSessionPrivate::doHandshake() {
    const auto token = alive;
    setState(QSshSession::Handshaking);
    while (*token && !terminated) {
        const int result = ssh_connect(handle);
        if (result == SSH_ERROR) {
            fail(QSshSession::HandshakeError, sshError());
            co_return;
        }
        // 首次 ssh_connect 才创建内部 poll 上下文，之后交给 Session 的统一驱动器。
        if (!event) {
            event = ssh_event_new();
            if (!event || ssh_event_add_session(event, handle) != SSH_OK) {
                fail(QSshSession::HandshakeError, QStringLiteral("Unable to attach SSH event loop"));
                co_return;
            }
        }
        if (result == SSH_OK) {
            co_return;
        }
        armWaits();
        if (!co_await doWaitForProgress()) {
            co_return;
        }
    }
}

asio::awaitable<void> QSshSessionPrivate::doVerifyHost() {
    const auto token = alive;
    verifyHost();
    while (*token && !terminated && pendingRequest) {
        if (!co_await doWaitForProgress()) {
            co_return;
        }
    }
}

asio::awaitable<void> QSshSessionPrivate::doAuthenticate() {
    const auto token = alive;
    while (*token && !terminated && phase == QSshSession::Authenticating) {
        if (pendingRequest) {
            if (!co_await doWaitForProgress()) {
                co_return;
            }
            continue;
        }
        const bool advanced = advanceAuthentication();
        if (terminated || phase != QSshSession::Authenticating) {
            co_return;
        }
        if (advanced) {
            // 立即完成的状态转换也让出一次执行权，无需人为规定每批推进多少步。
            co_await asio::post(client->io, asio::use_awaitable);
        } else {
            armWaits();
            if (!co_await doWaitForProgress()) {
                co_return;
            }
        }
    }
}

asio::awaitable<bool> QSshSessionPrivate::doWaitForProgress() {
    const auto token = alive;
    // timer 仅用作可取消的唤醒事件，不定期到期；所有通知和等待都在同一 worker 串行执行。
    progressEvent.expires_at(asio::steady_timer::time_point::max());
    asio::error_code ignored;
    co_await progressEvent.async_wait(asio::redirect_error(asio::use_awaitable, ignored));
    co_return *token && !terminated;
}

asio::awaitable<void> QSshSessionPrivate::doWaitSocket(const asio::ip::tcp::socket::wait_type type) {
    const auto token = alive;
    asio::error_code error;
    co_await socket.async_wait(type, asio::redirect_error(asio::use_awaitable, error));
    if (!*token || terminated) {
        co_return;
    }
    (type == asio::ip::tcp::socket::wait_read ? readWaiting : writeWaiting) = false;
    if (error) {
        fail(QSshSession::NetworkError, QString::fromStdString(error.message()));
    } else {
        schedule();
    }
}

void QSshSessionPrivate::schedule() {
    if (scheduled || terminated) {
        return;
    }
    scheduled = true;
    submit([this] {
        scheduled = false;
        drive();
    });
}

void QSshSessionPrivate::drive() {
    if (terminated || !handle) {
        return;
    }
    if (event) {
        // 零超时 poll 仅推进当前就绪协议事件，不阻塞唯一 worker。
        if (const int result = ssh_event_dopoll(event, 0); result == SSH_ERROR) {
            fail(QSshSession::NetworkError, sshError());
            return;
        }
    }
    if (!terminated && phase != QSshSession::Handshaking && !ssh_is_connected(handle)) {
        fail(QSshSession::NetworkError, QStringLiteral("SSH connection closed by peer"));
        return;
    }
    if (!terminated && phase == QSshSession::Ready) {
        bool progress = advanceForwarding();
        for (auto *channel : channels) {
            progress = channel->advance() || progress;
            if (terminated) {
                return;
            }
        }
        for (auto *sftp : sftpSessions) {
            progress = sftp->advance() || progress;
        }
        if (progress) {
            schedule();
        }
    }
    if (!terminated) {
        // Socket 事件、主机确认和 OTP 回答都唤醒同一条连接协程。
        progressEvent.cancel_one();
        armWaits();
    }
}

void QSshSessionPrivate::armWaits() {
    if (terminated || !handle || !socket.is_open()) {
        return;
    }

    const int flags = ssh_get_poll_flags(handle);
    if (!readWaiting && flags & SSH_READ_PENDING) {
        readWaiting = true;
        launch(doWaitSocket(asio::ip::tcp::socket::wait_read));
    }

    if (!writeWaiting && flags & SSH_WRITE_PENDING) {
        writeWaiting = true;
        launch(doWaitSocket(asio::ip::tcp::socket::wait_write));
    }
}

QString QSshSessionPrivate::sshError() const {
    return handle ? QString::fromUtf8(ssh_get_error(handle)) : QStringLiteral("SSH session is unavailable");
}

void QSshSessionPrivate::verifyHost() {
    setState(QSshSession::VerifyingHost);
    const auto known = ssh_session_is_known_server(handle);
    if (known == SSH_KNOWN_HOSTS_OK) {
        setState(QSshSession::Authenticating);
        return;
    }
    if (known == SSH_KNOWN_HOSTS_ERROR) {
        fail(QSshSession::HostKeyError, sshError());
        return;
    }
    ssh_key key = nullptr;
    unsigned char *hash = nullptr;
    size_t hashLength = 0;
    if (ssh_get_server_publickey(handle, &key) != SSH_OK ||
        ssh_get_publickey_hash(key, SSH_PUBLICKEY_HASH_SHA256, &hash, &hashLength) != SSH_OK) {
        ssh_key_free(key);
        ssh_clean_pubkey_hash(&hash);
        fail(QSshSession::HostKeyError, sshError());
        return;
    }
    char *fingerprint = ssh_get_fingerprint_hash(SSH_PUBLICKEY_HASH_SHA256, hash, hashLength);
    if (!fingerprint) {
        ssh_key_free(key);
        ssh_clean_pubkey_hash(&hash);
        fail(QSshSession::HostKeyError, QStringLiteral("Unable to compute host key fingerprint"));
        return;
    }
    QSshSession::HostKeyChallenge challenge;
    challenge.requestId = pendingRequest = ++nextRequestId;
    challenge.host = options.host;
    challenge.port = options.port;
    challenge.keyType = QString::fromLatin1(ssh_key_type_to_char(ssh_key_type(key)));
    challenge.fingerprint = QString::fromLatin1(fingerprint);
    challenge.status = known == SSH_KNOWN_HOSTS_CHANGED ? QSshSession::ChangedHostKey
                       : known == SSH_KNOWN_HOSTS_OTHER ? QSshSession::DifferentKeyType
                                                        : QSshSession::UnknownHost;
    ssh_string_free_char(fingerprint);
    ssh_clean_pubkey_hash(&hash);
    ssh_key_free(key);
    promptCount = -1;
    notify([this, challenge] {
        if (!frontClosing) {
            Q_EMIT q->hostVerificationRequired(challenge);
        }
    });
}

void QSshSessionPrivate::finishInteraction() {
    if (!pendingRequest) {
        return;
    }
    const quint64 id = pendingRequest;
    pendingRequest = 0;
    promptCount = -1;
    notify([this, id] { Q_EMIT q->interactionFinished(id); });
}

void QSshSessionPrivate::answerHost(const quint64 requestId, const QSshSession::HostKeyDecision decision) {
    if (terminated || !requestId || requestId != pendingRequest || phase != QSshSession::VerifyingHost) {
        return;
    }
    if (decision != QSshSession::AcceptOnce && decision != QSshSession::AcceptAndSave) {
        fail(QSshSession::HostKeyError, QStringLiteral("Server host key rejected"));
        return;
    }
    if (decision == QSshSession::AcceptAndSave && ssh_session_update_known_hosts(handle) != SSH_OK) {
        fail(QSshSession::HostKeyError, sshError());
        return;
    }
    finishInteraction();
    setState(QSshSession::Authenticating);
    schedule();
}

void QSshSessionPrivate::answerAuth(const quint64 requestId, const QStringList &answers) {
    if (terminated || !requestId || requestId != pendingRequest || phase != QSshSession::Authenticating ||
        promptCount < 0 || answers.size() != promptCount) {
        return;
    }

    for (int i = 0; i < answers.size(); ++i) {
        if (const QByteArray answer = answers[i].toUtf8();
            answer.contains('\0') || ssh_userauth_kbdint_setanswer(handle, i, answer.constData()) != SSH_OK) {
            fail(QSshSession::AuthenticationError, QStringLiteral("Unable to submit authentication answer"));
            return;
        }
    }

    finishInteraction();
    schedule();
}

bool QSshSessionPrivate::advanceAuthentication() {
    if (!authProbed) {
        const int result = ssh_userauth_none(handle, nullptr);
        if (result == SSH_AUTH_AGAIN) {
            return false;
        }
        authProbed = true;
        if (result == SSH_AUTH_SUCCESS || result == SSH_AUTH_ERROR) {
            return authResult(result);
        }
        return true;
    }
    if (authIndex < 0) {
        const int available = ssh_userauth_list(handle, nullptr);
        for (int i = 0; i < options.authenticationMethods.size(); ++i) {
            const auto method = options.authenticationMethods[i];
            const int flag = methodFlag(method);
            if (!(available & flag) || triedMethods & flag ||
                (method == QSshSession::PublicKey && options.privateKeyFile.isEmpty())) {
                continue;
            }
            authIndex = i;
            triedMethods |= flag;
            break;
        }
        if (authIndex < 0) {
            fail(QSshSession::AuthenticationError, QStringLiteral("No configured authentication method succeeded"));
            return false;
        }
        if (++authRounds > 128) {
            fail(QSshSession::AuthenticationError, QStringLiteral("Too many authentication rounds"));
            return false;
        }
    }
    int result = SSH_AUTH_ERROR;
    switch (options.authenticationMethods[authIndex]) {
        case QSshSession::PublicKey: {
            if (!privateKey) {
                const auto path = QFile::encodeName(options.privateKeyFile);
                // 显式传入口令且不注册同步询问回调，worker 不会等待 UI 输入。
                if (const auto passphrase = options.privateKeyPassphrase.toUtf8();
                    ssh_pki_import_privkey_file(path.constData(), passphrase.constData(), nullptr, nullptr,
                                                &privateKey) != SSH_OK) {
                    fail(QSshSession::AuthenticationError, QStringLiteral("Unable to load or decrypt private key"));
                    return false;
                }
            }
            result = ssh_userauth_publickey(handle, nullptr, privateKey);
            break;
        }
        case QSshSession::Password: {
            const auto password = options.password.toUtf8();
            result = ssh_userauth_password(handle, nullptr, password.constData());
            break;
        }
        case QSshSession::KeyboardInteractive:
            result = ssh_userauth_kbdint(handle, nullptr, nullptr);
            break;
    }
    return authResult(result);
}

bool QSshSessionPrivate::authResult(const int result) {
    switch (result) {
        case SSH_AUTH_AGAIN:
            return false;
        case SSH_AUTH_SUCCESS:
            deadline.cancel();
            options.password.clear();
            options.privateKeyPassphrase.clear();
            ssh_key_free(privateKey);
            privateKey = nullptr;
            setState(QSshSession::Ready);
            return false;
        case SSH_AUTH_PARTIAL:
            // 已完成的因素属于当前连接；只刷新后续方法，绝不重新建连或做 none 探测。
            triedMethods = 0;
            [[fallthrough]];
        case SSH_AUTH_DENIED:
            authIndex = -1;
            ssh_key_free(privateKey);
            privateKey = nullptr;
            return true;
        case SSH_AUTH_INFO: {
            if (++authRounds > 128) {
                fail(QSshSession::AuthenticationError, QStringLiteral("Too many authentication challenges"));
                return false;
            }
            const int count = ssh_userauth_kbdint_getnprompts(handle);
            if (count < 0 || count > 128) {
                fail(QSshSession::AuthenticationError, QStringLiteral("Invalid authentication challenge"));
                return false;
            }
            QSshSession::AuthChallenge challenge;
            challenge.requestId = pendingRequest = ++nextRequestId;
            challenge.name = QString::fromUtf8(ssh_userauth_kbdint_getname(handle));
            challenge.instruction = QString::fromUtf8(ssh_userauth_kbdint_getinstruction(handle));
            promptCount = count;
            for (int i = 0; i < count; ++i) {
                char echo = 0;
                const char *text = ssh_userauth_kbdint_getprompt(handle, i, &echo);
                challenge.prompts.append({.text = QString::fromUtf8(text), .echo = echo != 0});
            }
            notify([this, challenge] {
                if (!frontClosing) {
                    Q_EMIT q->authenticationChallenge(challenge);
                }
            });
            // 零问题轮次只展示说明，不等待不存在的用户输入。
            if (count == 0) {
                answerAuth(challenge.requestId, {});
            }
            return false;
        }
        default:
            fail(QSshSession::AuthenticationError, sshError());
            return false;
    }
}

void QSshSessionPrivate::fail(QSshSession::Error error, const QString &message) {
    if (terminated) {
        return;
    }
    notify([this, error, message] {
        frontError = error;
        frontErrorText = message;
        Q_EMIT q->errorOccurred(error);
    });
    shutdown();
}

void QSshSessionPrivate::shutdown() {
    if (terminated) {
        return;
    }
    terminated = true;
    setState(QSshSession::Closing);
    finishInteraction();
    resolver.cancel();
    deadline.cancel();
    progressEvent.cancel();
    shutdownForwarding();
    for (auto *sftp : sftpSessions) {
        sftp->shutdown(QSshSftpSession::SessionClosed, QStringLiteral("SSH session closed"));
    }
    for (auto *channel : channels) {
        if (channel->phase != QSshChannel::Closed) {
            channel->fail(QSshChannel::SessionClosed, QStringLiteral("SSH session closed"));
        }
    }
    // Channel 必须先释放；随后撤销事件关联，最后释放 Session 并关闭借给 libssh 的 socket。
    if (event) {
        ssh_event_remove_session(event, handle);
        ssh_event_free(event);
        event = nullptr;
    }
    ssh_key_free(privateKey);
    privateKey = nullptr;
    if (handle) {
        ssh_disconnect(handle);
        ssh_free(handle);
        handle = nullptr;
    }
    asio::error_code ignored;
    ignored = socket.close(ignored);
    options.password.clear();
    options.privateKeyPassphrase.clear();
    options.proxy.password.clear();
    setState(QSshSession::Closed);
}

QSshSession::QSshSession(QSshClient *client) : QObject(client), d_ptr(new QSshSessionPrivate(this, client)) {
    qRegisterMetaType<State>();
    qRegisterMetaType<Error>();
    qRegisterMetaType<Options>();
    qRegisterMetaType<HostKeyDecision>();
    qRegisterMetaType<HostKeyChallenge>();
    qRegisterMetaType<AuthChallenge>();
}

QSshSession::~QSshSession() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    d->frontClosing = true;
    while (const auto *sftp = findChild<QSshSftpSession *>(QString(), Qt::FindDirectChildrenOnly)) {
        delete sftp;
    }
    while (const auto *tunnel = findChild<QSshTunnel *>(QString(), Qt::FindDirectChildrenOnly)) {
        delete tunnel;
    }
    while (const auto *channel = findChild<QSshChannel *>(QString(), Qt::FindDirectChildrenOnly)) {
        delete channel;
    }
}

QSshSession::State QSshSession::state() const {
    Q_D(const QSshSession);
    return d->frontState;
}

QSshSession::Error QSshSession::error() const {
    Q_D(const QSshSession);
    return d->frontError;
}

QString QSshSession::errorString() const {
    Q_D(const QSshSession);
    return d->frontErrorText;
}

QSshShellChannel *QSshSession::createShellChannel() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    return d->frontClosing || d->frontState == Closed ? nullptr : new QSshShellChannel(this);
}

QSshExecChannel *QSshSession::createExecChannel() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    return d->frontClosing || d->frontState == Closed ? nullptr : new QSshExecChannel(this);
}

QSshTunnel *QSshSession::createTunnel() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    return d->frontClosing || d->frontState == Closed ? nullptr : new QSshTunnel(this);
}

QSshSftpSession *QSshSession::createSftpSession() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    return d->frontClosing || d->frontState == Closed ? nullptr : new QSshSftpSession(this);
}

void QSshSession::connectToHost(const Options &options) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    if (d->started || d->frontClosing || d->frontState != Idle) {
        return;
    }
    d->started = true;
    if (options.host.isEmpty() || options.host.contains(QChar::Null) || options.username.isEmpty() ||
        options.username.contains(QChar::Null) || !options.port || options.connectTimeoutMs <= 0 ||
        options.authenticationTimeoutMs <= 0 || options.channelTimeoutMs <= 0 ||
        options.authenticationMethods.isEmpty() || options.password.contains(QChar::Null) ||
        options.privateKeyFile.contains(QChar::Null) || options.privateKeyPassphrase.contains(QChar::Null) ||
        options.knownHostsFile.contains(QChar::Null) || options.host.contains('\r') || options.host.contains('\n') ||
        options.proxy.type < NoProxy || options.proxy.type > Socks4Proxy ||
        (options.proxy.type != NoProxy &&
         (options.proxy.host.isEmpty() || !options.proxy.port || options.proxy.host.contains(QChar::Null) ||
          options.proxy.username.toUtf8().size() > 255 || options.proxy.password.toUtf8().size() > 255 ||
          (options.proxy.type == Socks4Proxy && options.proxy.username.contains(QChar::Null)))) ||
        options.agentSocketPath.contains(QChar::Null) || options.x11.display.contains(QChar::Null)) {
        d->submit([d] { d->fail(InvalidConfiguration, QStringLiteral("Invalid SSH connection options")); });
        return;
    }
    d->frontState = Connecting;
    d->submit([d, options] { d->start(options); });
    Q_EMIT stateChanged(Connecting);
}

void QSshSession::answerHostVerification(quint64 requestId, HostKeyDecision decision) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    d->submit([d, requestId, decision] { d->answerHost(requestId, decision); });
}

void QSshSession::answerAuthentication(quint64 requestId, const QStringList &answers) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    d->submit([d, requestId, answers] { d->answerAuth(requestId, answers); });
}

void QSshSession::cancelAuthentication(quint64 requestId) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    d->submit([d, requestId] {
        if (requestId && requestId == d->pendingRequest) {
            d->fail(Cancelled, QStringLiteral("SSH authentication cancelled"));
        }
    });
}

void QSshSession::close() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshSession);
    if (d->frontClosing || d->frontState == Closed) {
        return;
    }
    d->frontClosing = true;
    d->submit([d] { d->shutdown(); });
}
