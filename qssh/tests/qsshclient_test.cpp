#include "qssh_test_support.h"

#include <QPointer>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>
#include <qssh/qsshshellchannel.h>

using qssh_test::waitUntil;

/** 仅依赖公共 Qt 头文件，验证配置校验和未连接对象生命周期。 */
TEST(QSshLifecycle, ValidationAndOwnership) {
    QSshClient client;
    auto *session = client.createSession();
    ASSERT_NE(session, nullptr);
    EXPECT_EQ(session->state(), QSshSession::Idle);
    auto *shell = session->createShellChannel();
    EXPECT_EQ(shell->terminalSize(), QSize(80, 24));
    EXPECT_FALSE(shell->setTerminalSize({0, 24}));
    EXPECT_FALSE(shell->setTerminalSize({80, 32768}));
    EXPECT_EQ(shell->write("input"), -1);
    EXPECT_EQ(shell->bytesAvailable(), 0);
    EXPECT_EQ(shell->bytesToWrite(), 0);
    EXPECT_FALSE(shell->hasExitCode());
    EXPECT_EQ(shell->exitCode(), -1);
    session->connectToHost({});
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(session->error(), QSshSession::InvalidConfiguration);
    EXPECT_EQ(shell->state(), QSshChannel::Closed);
    client.close();
    EXPECT_EQ(client.createSession(), nullptr);
}

/** 检查关闭的幂等性以及父对象先于 worker 释放全部子对象。 */
TEST(QSshLifecycle, CloseAndDestruction) {
    for (int i = 0; i < 20; ++i) {
        QSshClient client;
        auto *session = client.createSession();
        QPointer<QSshChannel> channel = session->createExecChannel();
        int closed = 0;
        QObject::connect(session, &QSshSession::closed, session, [&] { ++closed; });
        session->close();
        session->close();
        ASSERT_TRUE(waitUntil([&] { return closed == 1; }));
        session->close();
        delete session;
        EXPECT_TRUE(channel.isNull());
    }
}

/** 验证父对象析构时拒绝新建子对象，并允许 destroyed 槽提前删除兄弟对象。 */
TEST(QSshLifecycle, ReentrantChildDestruction) {
    auto *client = new QSshClient;
    auto *first = client->createSession();
    QPointer<QSshSession> second = client->createSession();
    auto *channel = first->createExecChannel();
    QPointer<QSshChannel> sibling = first->createShellChannel();
    QObject::connect(channel, &QObject::destroyed, first, [&] {
        EXPECT_EQ(first->createExecChannel(), nullptr);
        delete sibling.data();
    });
    QObject::connect(first, &QObject::destroyed, client, [&] {
        EXPECT_EQ(client->createSession(), nullptr);
        delete second.data();
    });
    delete client;
    EXPECT_TRUE(second.isNull());
    EXPECT_TRUE(sibling.isNull());
}
