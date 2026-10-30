#include "qssh_test_support.h"

#include <QCoreApplication>
#include <QDir>
#include <QFileInfo>
#include <QTcpServer>
#include <QTcpSocket>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>

using qssh_test::waitUntil;

/** 验证 HTTP CONNECT、SOCKS5、SOCKS4a 代理及凭据，目标 DNS 必须交给代理。 */
TEST_F(QSshIntegration, UpstreamProxies) {
    QSshClient client;
    for (auto type : {QSshSession::HttpConnectProxy, QSshSession::Socks5Proxy, QSshSession::Socks4Proxy}) {
        for (bool authenticated : {false, true}) {
            QProcess proxy;
            const auto script = QFileInfo(QString::fromUtf8(QSSH_TEST_SERVER))
                                    .dir()
                                    .filePath(QStringLiteral("proxy_test_server.py"));
            const QString protocol = type == QSshSession::HttpConnectProxy ? QStringLiteral("http")
                                     : type == QSshSession::Socks5Proxy    ? QStringLiteral("socks5")
                                                                           : QStringLiteral("socks4");
            proxy.start(QString::fromUtf8(QSSH_TEST_PYTHON), {script, protocol, authenticated ? "auth" : "none"});
            ASSERT_TRUE(proxy.waitForStarted());
            QByteArray startup;
            ASSERT_TRUE(waitUntil([&] {
                startup += proxy.readAllStandardOutput();
                return startup.contains('\n');
            }));
            auto configuration = options();
            configuration.host = QStringLiteral("proxy-target.invalid");
            configuration.proxy.type = type;
            configuration.proxy.host = QStringLiteral("127.0.0.1");
            configuration.proxy.port = startup.trimmed().toUShort();
            if (authenticated) {
                configuration.proxy.username = QStringLiteral("proxy-user");
                configuration.proxy.password = QStringLiteral("proxy-password");
            }
            auto *session = client.createSession();
            QObject::connect(session, &QSshSession::hostVerificationRequired, session,
                             [session](const QSshSession::HostKeyChallenge &challenge) {
                                 EXPECT_EQ(challenge.host, QStringLiteral("proxy-target.invalid"));
                                 session->answerHostVerification(challenge.requestId, QSshSession::AcceptOnce);
                             });
            session->connectToHost(configuration);
            ASSERT_TRUE(waitUntil(
                [&] { return session->state() == QSshSession::Ready || session->state() == QSshSession::Closed; }));
            EXPECT_EQ(session->state(), QSshSession::Ready) << session->errorString().toStdString();
            if (session->state() == QSshSession::Ready) {
                auto *exec = session->createExecChannel();
                exec->open(QStringLiteral("report"));
                ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
                EXPECT_EQ(exec->exitCode(), 7);
            }
            delete session;
            proxy.kill();
            proxy.waitForFinished();
        }
    }
}

/** 验证上游代理认证失败不会进入 SSH 认证阶段。 */
TEST_F(QSshIntegration, ProxyAuthenticationRejected) {
    for (const auto &protocol : {QStringLiteral("http"), QStringLiteral("socks5")}) {
        QProcess proxy;
        const auto script = QFileInfo(QString::fromUtf8(QSSH_TEST_SERVER))
                                .dir()
                                .filePath(QStringLiteral("proxy_test_server.py"));
        proxy.start(QString::fromUtf8(QSSH_TEST_PYTHON), {script, protocol, "auth"});
        ASSERT_TRUE(proxy.waitForStarted());
        QByteArray startup;
        ASSERT_TRUE(waitUntil([&] {
            startup += proxy.readAllStandardOutput();
            return startup.contains('\n');
        }));
        QSshClient client;
        auto *session = client.createSession();
        auto configuration = options();
        configuration.proxy.type = protocol == QStringLiteral("http") ? QSshSession::HttpConnectProxy
                                                                      : QSshSession::Socks5Proxy;
        configuration.proxy.host = QStringLiteral("127.0.0.1");
        configuration.proxy.port = startup.trimmed().toUShort();
        configuration.proxy.username = QStringLiteral("proxy-user");
        configuration.proxy.password = QStringLiteral("wrong");
        session->connectToHost(configuration);
        ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
        EXPECT_EQ(session->error(), QSshSession::NetworkError);
        EXPECT_FALSE(session->errorString().contains(QStringLiteral("wrong")));
        proxy.kill();
        proxy.waitForFinished();
    }
}

/** 验证等待上游代理响应期间删除 Session，可以安全撤销读取协程。 */
TEST(QSshLifecycle, CancelProxyNegotiation) {
    QTcpServer proxy;
    ASSERT_TRUE(proxy.listen(QHostAddress::LocalHost, 0));
    QSshClient client;
    QSshSession::Options configuration;
    configuration.host = QStringLiteral("proxy-target.invalid");
    configuration.username = QStringLiteral("test");
    configuration.proxy.type = QSshSession::HttpConnectProxy;
    configuration.proxy.host = QStringLiteral("127.0.0.1");
    configuration.proxy.port = proxy.serverPort();
    for (int i = 0; i < 10; ++i) {
        auto *session = client.createSession();
        session->connectToHost(configuration);
        ASSERT_TRUE(waitUntil([&] { return proxy.hasPendingConnections(); }));
        auto *socket = proxy.nextPendingConnection();
        ASSERT_TRUE(waitUntil([&] { return socket->bytesAvailable() != 0; }));
        delete session;
        delete socket;
        QCoreApplication::processEvents();
    }
}
