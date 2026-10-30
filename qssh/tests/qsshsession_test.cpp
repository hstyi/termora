#include "qssh_test_support.h"

#include <QCoreApplication>
#include <QTcpServer>
#include <QTcpSocket>
#include <qssh/qsshclient.h>

using qssh_test::waitUntil;

/** 验证 TCP 已建立但服务器不发送 SSH 握手时，整体连接时限仍生效。 */
TEST(QSshLifecycle, HandshakeTimeoutAndPendingDestruction) {
    QTcpServer stalled;
    ASSERT_TRUE(stalled.listen(QHostAddress::LocalHost, 0));
    QSshClient client;
    auto *session = client.createSession();
    QSshSession::Options configuration;
    configuration.host = QStringLiteral("127.0.0.1");
    configuration.port = stalled.serverPort();
    configuration.username = QStringLiteral("test");
    configuration.connectTimeoutMs = 200;
    session->connectToHost(configuration);
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(session->error(), QSshSession::TimeoutError);
    EXPECT_TRUE(stalled.hasPendingConnections());
    for (int i = 0; i < 30; ++i) {
        auto *pending = client.createSession();
        pending->connectToHost(configuration);
        delete pending;
    }
    // 显式释放已接受的 socket，避免测试服务析构时仍持有连接。
    while (stalled.hasPendingConnections()) {
        delete stalled.nextPendingConnection();
    }
}

/** 验证握手协程挂起时删除 Session，取消后的恢复不会访问已释放的 Private。 */
TEST(QSshLifecycle, DeleteSuspendedHandshakeCoroutine) {
    QTcpServer stalled;
    ASSERT_TRUE(stalled.listen(QHostAddress::LocalHost, 0));
    QSshClient client;
    QSshSession::Options configuration;
    configuration.host = QStringLiteral("127.0.0.1");
    configuration.port = stalled.serverPort();
    configuration.username = QStringLiteral("test");
    for (int i = 0; i < 20; ++i) {
        auto *session = client.createSession();
        session->connectToHost(configuration);
        ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Handshaking; }));
        delete session;
        QCoreApplication::processEvents();
        while (stalled.hasPendingConnections()) {
            delete stalled.nextPendingConnection();
        }
    }
}
