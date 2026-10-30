#include "qssh_test_support.h"

#include <QLocalServer>
#include <QLocalSocket>
#include <QTcpServer>
#include <QTcpSocket>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>

using qssh_test::waitUntil;

/** 验证 Agent 入站 Channel 与本地 Unix socket／Windows OpenSSH 命名管道双向转接。 */
TEST_F(QSshIntegration, AgentForwarding) {
    QLocalServer agent;
    ASSERT_TRUE(agent.listen(directory.path() + QStringLiteral("/agent")));
    QByteArray request;
    QObject::connect(&agent, &QLocalServer::newConnection, &agent, [&] {
        auto *socket = agent.nextPendingConnection();
        QObject::connect(socket, &QLocalSocket::readyRead, socket, [socket, &request] {
            request += socket->readAll();
            if (request.size() == 5) {
                socket->write(QByteArray("\0\0\0\1\5", 5));
                socket->flush();
            }
        });
    });
    QSshClient client;
    auto *session = client.createSession();
    acceptHost(session);
    auto configuration = options();
    configuration.agentForwarding = true;
    configuration.agentSocketPath = agent.fullServerName();
    session->connectToHost(configuration);
    ASSERT_TRUE(
        waitUntil([&] { return session->state() == QSshSession::Ready || session->state() == QSshSession::Closed; }));
    ASSERT_EQ(session->state(), QSshSession::Ready) << session->errorString().toStdString();
    auto *exec = session->createExecChannel();
    exec->open(QStringLiteral("agent"));
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
    EXPECT_EQ(exec->readAll(), QByteArray("\0\0\0\1\5", 5)) << server.readAllStandardError().constData();
    EXPECT_EQ(request, QByteArray("\0\0\0\1\x0b", 5));
}

/** 验证 X11 替代 cookie 在客户端校验并替换，伪造 cookie 不得接触本地显示。 */
TEST_F(QSshIntegration, X11CookieVerificationAndReplacement) {
    QTcpServer display;
    ASSERT_TRUE(display.listen(QHostAddress::LocalHost, 0));
    ASSERT_GT(display.serverPort(), 6000);
    int connections = 0;
    QByteArray setup;
    QObject::connect(&display, &QTcpServer::newConnection, &display, [&] {
        ++connections;
        auto *socket = display.nextPendingConnection();
        QObject::connect(socket, &QTcpSocket::readyRead, socket, [socket, &setup] {
            setup += socket->readAll();
            if (setup.size() >= 56) {
                socket->write("X11-OK");
                socket->flush();
            }
        });
    });
    QSshClient client;
    auto *session = client.createSession();
    acceptHost(session);
    auto configuration = options();
    configuration.x11.enabled = true;
    configuration.x11.display = QStringLiteral("127.0.0.1:%1.0").arg(display.serverPort() - 6000);
    configuration.x11.authenticationCookie = QByteArray(16, 'A');
    session->connectToHost(configuration);
    ASSERT_TRUE(
        waitUntil([&] { return session->state() == QSshSession::Ready || session->state() == QSshSession::Closed; }));
    ASSERT_EQ(session->state(), QSshSession::Ready) << session->errorString().toStdString();
    auto *exec = session->createExecChannel();
    exec->open(QStringLiteral("x11"));
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
    EXPECT_EQ(exec->readAll(), QByteArray("X11-OK")) << server.readAllStandardError().constData();
    ASSERT_GE(setup.size(), 56);
    EXPECT_EQ(setup.mid(32, 16), QByteArray(16, 'A'));
    EXPECT_EQ(setup.mid(48), QByteArray("X11-DATA"));
    EXPECT_EQ(connections, 1);
    auto *bad = session->createExecChannel();
    bad->open(QStringLiteral("x11-bad-cookie"));
    ASSERT_TRUE(waitUntil([&] { return bad->state() == QSshChannel::Closed; }));
    EXPECT_EQ(bad->readAll(), QByteArray("FORWARD-REJECTED"));
    EXPECT_EQ(connections, 1);
}

/** 未显式启用时，拒绝服务器主动打开 Agent 与 X11 Channel。 */
TEST_F(QSshIntegration, UnrequestedForwardingIsRejected) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    for (const auto &command : {QStringLiteral("agent"), QStringLiteral("x11")}) {
        auto *exec = session->createExecChannel();
        exec->open(command);
        ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
        EXPECT_EQ(exec->readAll(), QByteArray("FORWARD-REJECTED"));
    }
}
