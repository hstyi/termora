#include "qssh_test_support.h"

#include <QCoreApplication>
#include <QTcpServer>
#include <QTcpSocket>
#include <memory>
#include <qssh/qsshclient.h>
#include <qssh/qsshtunnel.h>

using qssh_test::waitUntil;

/** 负数间隔在建连前被拒绝，0 表示关闭。 */
TEST(QSshLifecycle, InvalidKeepAliveInterval) {
    QSshClient client;
    auto *session = client.createSession();
    QSshSession::Options configuration;
    EXPECT_EQ(configuration.serverAliveIntervalMs, 0);
    configuration.host = QStringLiteral("127.0.0.1");
    configuration.username = QStringLiteral("test");
    configuration.serverAliveIntervalMs = -1;
    session->connectToHost(configuration);
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(session->error(), QSshSession::InvalidConfiguration);
}

/** 默认不发送 SSH 保活包，TCP 保活开关不改变这一点。 */
TEST_F(QSshIntegration, SshKeepAliveDisabledByDefault) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    QByteArray messages;
    EXPECT_FALSE(waitUntil(
        [&] {
            messages += server.readAllStandardOutput();
            return messages.contains("SSH_KEEPALIVE\n");
        },
        200));
    EXPECT_EQ(session->state(), QSshSession::Ready);
}

/** 认证完成后才启动保活；服务端不应答也继续保持 Ready，关闭后停止发送。 */
TEST_F(QSshIntegration, SshKeepAliveStartsWhenReadyAndStopsOnClose) {
    QSshClient client;
    auto *session = client.createSession();
    quint64 challenge = 0;
    QObject::connect(session, &QSshSession::hostVerificationRequired, session,
                     [&](const auto &request) { challenge = request.requestId; });
    auto configuration = options();
    configuration.tcpKeepAlive = false;
    configuration.serverAliveIntervalMs = 40;
    session->connectToHost(configuration);
    ASSERT_TRUE(waitUntil([&] { return challenge != 0; }));
    QByteArray messages;
    const auto received = [&] {
        messages += server.readAllStandardOutput();
        return messages.count("SSH_KEEPALIVE\n");
    };
    EXPECT_FALSE(waitUntil([&] { return received() != 0; }, 120));
    session->answerHostVerification(challenge, QSshSession::AcceptOnce);
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Ready; }));
    ASSERT_TRUE(waitUntil([&] { return received() >= 5; }));
    EXPECT_EQ(session->state(), QSshSession::Ready);
    EXPECT_EQ(session->error(), QSshSession::NoError);
    session->close();
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    ASSERT_TRUE(waitUntil([&] {
        received();
        return messages.contains("SSH_KEEPALIVE_CLOSED\n");
    }));
    const auto count = received();
    EXPECT_FALSE(waitUntil([&] { return received() != count; }, 120));
}

/** 未到期的保活定时器可随 Session 和 Client 销毁，取消回调不得访问旧 Private。 */
TEST_F(QSshIntegration, SshKeepAlivePendingTimerDestruction) {
    auto client = std::make_unique<QSshClient>();
    for (int i = 0; i < 4; ++i) {
        auto *session = client->createSession();
        acceptHost(session);
        auto configuration = options();
        configuration.serverAliveIntervalMs = 1000;
        session->connectToHost(configuration);
        ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Ready; }));
        if (i < 3) {
            delete session;
        }
    }
    client.reset();
    QCoreApplication::processEvents();
}

/** 保活包不占用全局请求状态，远端监听、数据转发和撤销监听仍能正常完成。 */
TEST_F(QSshIntegration, SshKeepAliveCoexistsWithRemoteForwarding) {
    QTcpServer echo;
    ASSERT_TRUE(echo.listen(QHostAddress::LocalHost, 0));
    QObject::connect(&echo, &QTcpServer::newConnection, &echo, [&] {
        while (echo.hasPendingConnections()) {
            auto *socket = echo.nextPendingConnection();
            QObject::connect(socket, &QTcpSocket::readyRead, socket, [socket] { socket->write(socket->readAll()); });
        }
    });
    QSshClient client;
    auto *session = client.createSession();
    acceptHost(session);
    auto configuration = options();
    configuration.serverAliveIntervalMs = 10;
    session->connectToHost(configuration);
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Ready; }));
    QByteArray messages;
    ASSERT_TRUE(waitUntil([&] {
        messages += server.readAllStandardOutput();
        return messages.count("SSH_KEEPALIVE\n") >= 3;
    }));
    for (int i = 0; i < 3; ++i) {
        auto *tunnel = session->createTunnel();
        QSshTunnel::Options forward;
        forward.type = QSshTunnel::Remote;
        forward.destinationHost = QStringLiteral("127.0.0.1");
        forward.destinationPort = echo.serverPort();
        tunnel->open(forward);
        ASSERT_TRUE(waitUntil(
            [&] { return tunnel->state() == QSshTunnel::Listening || tunnel->state() == QSshTunnel::Closed; }));
        ASSERT_EQ(tunnel->state(), QSshTunnel::Listening) << tunnel->errorString().toStdString();
        QTcpSocket peer;
        peer.connectToHost(QHostAddress::LocalHost, tunnel->boundPort());
        ASSERT_TRUE(waitUntil([&] { return peer.state() == QAbstractSocket::ConnectedState; }));
        const QByteArray payload = QByteArray("keepalive-forward\0", 18) + QByteArray(65536, 'x');
        peer.write(payload);
        QByteArray result;
        ASSERT_TRUE(waitUntil([&] {
            result += peer.readAll();
            return result.size() >= payload.size();
        }));
        EXPECT_EQ(result, payload);
        tunnel->close();
        ASSERT_TRUE(waitUntil([&] { return tunnel->state() == QSshTunnel::Closed; }));
        EXPECT_EQ(tunnel->error(), QSshTunnel::NoError) << tunnel->errorString().toStdString();
        delete tunnel;
    }
    EXPECT_EQ(session->state(), QSshSession::Ready);
}
