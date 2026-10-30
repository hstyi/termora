#include "qssh_test_support.h"

#include <QTcpServer>
#include <QTcpSocket>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>
#include <qssh/qsshtunnel.h>

using qssh_test::waitUntil;

namespace {
    /** 为真实隧道提供本地 TCP 回显目标；每条连接有独立 Qt socket。 */
    class EchoServer {
    public:
        /** 监听系统分配的回环端口，并原样回显收到的字节。 */
        EchoServer() {
            server.listen(QHostAddress::LocalHost, 0);
            QObject::connect(&server, &QTcpServer::newConnection, &server, [this] {
                while (server.hasPendingConnections()) {
                    auto *socket = server.nextPendingConnection();
                    QObject::connect(socket, &QTcpSocket::readyRead, socket,
                                     [socket] { socket->write(socket->readAll()); });
                }
            });
        }

        QTcpServer server; ///< 拥有所有已接受连接的本地测试监听器。
    };

    /** 在 Qt 事件循环中等待 socket 恰好收到 count 字节，超时返回已接收部分。 */
    QByteArray receive(QTcpSocket &socket, int count) {
        QByteArray result;
        waitUntil([&] {
            result += socket.readAll();
            return result.size() >= count;
        });
        return result;
    }
} // namespace

/** 验证 local 和 remote 隧道的数据收发、系统分配端口及监听撤销。 */
TEST_F(QSshIntegration, LocalAndRemoteTunnels) {
    EchoServer echo;
    ASSERT_TRUE(echo.server.isListening());
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    for (auto type : {QSshTunnel::Local, QSshTunnel::Remote}) {
        SCOPED_TRACE(type);
        auto *tunnel = session->createTunnel();
        QSshTunnel::Options configuration;
        configuration.type = type;
        configuration.destinationHost = QStringLiteral("127.0.0.1");
        configuration.destinationPort = echo.server.serverPort();
        tunnel->open(configuration);
        ASSERT_TRUE(waitUntil(
            [&] { return tunnel->state() == QSshTunnel::Listening || tunnel->state() == QSshTunnel::Closed; }));
        ASSERT_EQ(tunnel->state(), QSshTunnel::Listening) << tunnel->errorString().toStdString();
        ASSERT_NE(tunnel->boundPort(), 0);
        QTcpSocket socket;
        socket.connectToHost(QHostAddress::LocalHost, tunnel->boundPort());
        ASSERT_TRUE(waitUntil([&] { return socket.state() == QAbstractSocket::ConnectedState; }));
        const QByteArray payload = QByteArray("tunnel\0data", 11) + QByteArray(200000, 'x');
        socket.write(payload);
        EXPECT_EQ(receive(socket, payload.size()), payload) << server.readAllStandardError().constData();
        const auto port = tunnel->boundPort();
        tunnel->close();
        ASSERT_TRUE(waitUntil([&] { return tunnel->state() == QSshTunnel::Closed; }));
        EXPECT_EQ(tunnel->error(), QSshTunnel::NoError) << tunnel->errorString().toStdString();
        QTcpSocket rejected;
        rejected.connectToHost(QHostAddress::LocalHost, port);
        ASSERT_TRUE(waitUntil([&] { return rejected.state() == QAbstractSocket::UnconnectedState; }));
        delete tunnel;
    }
}

/** 验证动态隧道 SOCKS5 域名解析及 SOCKS4a，不由客户端解析远端目标域名。 */
TEST_F(QSshIntegration, DynamicSocksTunnel) {
    EchoServer echo;
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *tunnel = session->createTunnel();
    QSshTunnel::Options configuration;
    configuration.type = QSshTunnel::Dynamic;
    tunnel->open(configuration);
    ASSERT_TRUE(waitUntil([&] { return tunnel->state() == QSshTunnel::Listening; }));
    for (int version : {5, 4}) {
        QTcpSocket socket;
        socket.connectToHost(QHostAddress::LocalHost, tunnel->boundPort());
        ASSERT_TRUE(waitUntil([&] { return socket.state() == QAbstractSocket::ConnectedState; }));
        const QByteArray host("proxy-target.invalid");
        const auto port = echo.server.serverPort();
        QByteArray request;
        if (version == 5) {
            socket.write(QByteArray("\5\1\0", 3));
            ASSERT_EQ(receive(socket, 2), QByteArray("\5\0", 2));
            request = QByteArray("\5\1\0\3", 4) + char(host.size()) + host;
            request.append(char(port >> 8)).append(char(port));
        } else {
            request = QByteArray("\4\1", 2);
            request.append(char(port >> 8)).append(char(port));
            request += QByteArray("\0\0\0\1\0", 5) + host + '\0';
        }
        socket.write(request.left(1));
        socket.write(request.mid(1));
        const auto reply = receive(socket, version == 5 ? 10 : 8);
        ASSERT_EQ(reply.size(), version == 5 ? 10 : 8);
        EXPECT_EQ(quint8(reply[1]), version == 5 ? 0 : 90);
        const QByteArray payload("dynamic\0payload", 15);
        socket.write(payload);
        EXPECT_EQ(receive(socket, payload.size()), payload);
    }
}

/** 验证远端监听被拒绝时报告隧道错误，SSH 连接仍可使用。 */
TEST_F(QSshIntegration, RemoteTunnelRejectedAndPendingDeletion) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *tunnel = session->createTunnel();
    QSshTunnel::Options configuration;
    configuration.type = QSshTunnel::Remote;
    configuration.listenHost = QStringLiteral("reject.invalid");
    configuration.destinationHost = QStringLiteral("127.0.0.1");
    configuration.destinationPort = 12345;
    tunnel->open(configuration);
    ASSERT_TRUE(waitUntil([&] { return tunnel->state() == QSshTunnel::Closed; }));
    EXPECT_EQ(tunnel->error(), QSshTunnel::RequestRejected);
    configuration.listenHost = QStringLiteral("127.0.0.1");
    for (int i = 0; i < 10; ++i) {
        auto *pending = session->createTunnel();
        pending->open(configuration);
        delete pending;
    }
    auto *exec = session->createExecChannel();
    exec->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
    EXPECT_EQ(exec->exitCode(), 7) << session->errorString().toStdString() << exec->errorString().toStdString();
}

/** 验证未完成的 SOCKS 协商及活动监听在 Session 关闭时一起取消。 */
TEST_F(QSshIntegration, ClosingSessionCancelsForwarding) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *tunnel = session->createTunnel();
    QSshTunnel::Options configuration;
    configuration.type = QSshTunnel::Dynamic;
    tunnel->open(configuration);
    ASSERT_TRUE(waitUntil([&] { return tunnel->state() == QSshTunnel::Listening; }));
    QTcpSocket pending;
    pending.connectToHost(QHostAddress::LocalHost, tunnel->boundPort());
    ASSERT_TRUE(waitUntil([&] { return pending.state() == QAbstractSocket::ConnectedState; }));
    pending.write(QByteArray(1, '\5')); // 仅发送版本字节，让转发流挂起在 SOCKS 方法数量读取。
    pending.flush();
    session->close();
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(tunnel->state(), QSshTunnel::Closed);
    ASSERT_TRUE(waitUntil([&] { return pending.state() == QAbstractSocket::UnconnectedState; }));
    delete session;
}

/** 验证动态代理明确拒绝 UDP 等不支持的命令，监听器仍能接受下一条正常连接。 */
TEST_F(QSshIntegration, DynamicRejectsUnsupportedCommands) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *tunnel = session->createTunnel();
    QSshTunnel::Options configuration;
    configuration.type = QSshTunnel::Dynamic;
    tunnel->open(configuration);
    ASSERT_TRUE(waitUntil([&] { return tunnel->state() == QSshTunnel::Listening; }));
    QTcpSocket socket;
    socket.connectToHost(QHostAddress::LocalHost, tunnel->boundPort());
    ASSERT_TRUE(waitUntil([&] { return socket.state() == QAbstractSocket::ConnectedState; }));
    socket.write(QByteArray("\5\1\0", 3));
    ASSERT_EQ(receive(socket, 2), QByteArray("\5\0", 2));
    socket.write(QByteArray("\5\3\0\1", 4));
    const auto reply = receive(socket, 10);
    ASSERT_EQ(reply.size(), 10);
    EXPECT_EQ(quint8(reply[1]), 7);
    auto *exec = session->createExecChannel();
    exec->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
    EXPECT_EQ(exec->exitCode(), 7);
    EXPECT_EQ(tunnel->state(), QSshTunnel::Listening);
    QTcpSocket denied;
    denied.connectToHost(QHostAddress::LocalHost, tunnel->boundPort());
    ASSERT_TRUE(waitUntil([&] { return denied.state() == QAbstractSocket::ConnectedState; }));
    denied.write(QByteArray("\5\1\0", 3));
    ASSERT_EQ(receive(denied, 2), QByteArray("\5\0", 2));
    const QByteArray destination("reject.invalid");
    denied.write(QByteArray("\5\1\0\3", 4) + char(destination.size()) + destination + QByteArray("\0\x16", 2));
    const auto rejected = receive(denied, 10);
    ASSERT_EQ(rejected.size(), 10);
    EXPECT_EQ(quint8(rejected[1]), 5);
    ASSERT_TRUE(waitUntil([&] { return tunnel->error() == QSshTunnel::ConnectionError; }));
    EXPECT_EQ(tunnel->state(), QSshTunnel::Listening);
}
