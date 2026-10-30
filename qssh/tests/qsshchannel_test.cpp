#include "qssh_test_support.h"

#include <QPointer>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>
#include <qssh/qsshshellchannel.h>

using qssh_test::waitUntil;

/** 验证密码登录、stdout/stderr、退出码以及一个连接复用多个 Channel。 */
TEST_F(QSshIntegration, ExecAndMultiplexing) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *first = session->createExecChannel();
    auto *second = session->createExecChannel();
    first->open(QStringLiteral("report"));
    second->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] {
        return first->state() == QSshChannel::Closed && second->state() == QSshChannel::Closed;
    })) << first->errorString().toStdString()
        << second->errorString().toStdString();
    for (auto *channel : {first, second}) {
        EXPECT_EQ(channel->error(), QSshChannel::NoError) << channel->errorString().toStdString();
        EXPECT_EQ(channel->readAll(), QByteArray("stdout-\xe4\xbd\xa0\xe5\xa5\xbd\n"));
        EXPECT_EQ(channel->readAllStandardError(), QByteArray("stderr-marker\n"));
        EXPECT_TRUE(channel->hasExitCode());
        EXPECT_EQ(channel->exitCode(), 7);
    }
    EXPECT_EQ(session->state(), QSshSession::Ready);
}

/** 验证 shell 初始 PTY、动态尺寸、原始字节写入和 EOF 后的尾部输出。 */
TEST_F(QSshIntegration, ShellResizeAndEof) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *shell = session->createShellChannel();
    ASSERT_TRUE(shell->setTerminalSize({100, 30}));
    QByteArray output;
    QObject::connect(shell, &QSshChannel::readyRead, shell, [&] { output += shell->readAll(); });
    shell->open();
    ASSERT_TRUE(waitUntil([&] { return output.contains("SIZE=100,30"); })) << shell->errorString().toStdString();
    ASSERT_TRUE(shell->setTerminalSize({120, 40}));
    ASSERT_TRUE(waitUntil([&] { return output.contains("SIZE=120,40"); }));
    const QByteArray data("hello\0binary", 12);
    ASSERT_EQ(shell->write(data), data.size());
    shell->sendEof();
    EXPECT_EQ(shell->write("late"), -1);
    ASSERT_TRUE(waitUntil([&] { return shell->state() == QSshChannel::Closed; }));
    EXPECT_TRUE(output.contains(data));
    EXPECT_TRUE(output.contains("EOF-TAIL"));
    EXPECT_TRUE(shell->hasExitCode());
    EXPECT_EQ(shell->exitCode(), 0);
}

/** 验证被拒绝的 exec 请求只关闭自身，其他 Channel 仍可使用。 */
TEST_F(QSshIntegration, RejectedChannelIsIsolated) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *bad = session->createExecChannel();
    auto *good = session->createExecChannel();
    bad->open(QStringLiteral("reject"));
    good->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] { return bad->state() == QSshChannel::Closed && good->state() == QSshChannel::Closed; }));
    EXPECT_EQ(bad->error(), QSshChannel::OpenError);
    EXPECT_EQ(good->error(), QSshChannel::NoError);
    EXPECT_EQ(good->exitCode(), 7);
    EXPECT_EQ(session->state(), QSshSession::Ready);
}

/** 验证大量输出的有界接收及恢复，不消费它时其他 Channel 仍能完成。 */
TEST_F(QSshIntegration, ReceiveBackpressureAndRecovery) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *large = session->createExecChannel();
    large->open(QStringLiteral("large"));
    ASSERT_TRUE(waitUntil([&] { return large->bytesAvailable() == 4 * 1024 * 1024; }));
    auto *other = session->createExecChannel();
    other->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] { return other->state() == QSshChannel::Closed; }));
    EXPECT_EQ(other->exitCode(), 7);
    EXPECT_LE(large->bytesAvailable(), 4 * 1024 * 1024);
    qint64 received = 0;
    QByteArray errorOutput;
    ASSERT_TRUE(waitUntil([&] {
        const QByteArray output = large->readAll();
        EXPECT_EQ(output.count('x'), output.size());
        received += output.size();
        errorOutput += large->readAllStandardError();
        return large->state() == QSshChannel::Closed;
    }));
    EXPECT_EQ(received, 6 * 1024 * 1024);
    EXPECT_EQ(errorOutput, QByteArray("error-tail"));
    EXPECT_EQ(large->error(), QSshChannel::NoError);
}

/** 验证发送上限错误不会污染下一次 write，EOF 会在输入排空后发送。 */
TEST_F(QSshIntegration, WriteLimitAndRecovery) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *cat = session->createExecChannel();
    cat->open(QStringLiteral("cat"));
    ASSERT_TRUE(waitUntil([&] { return cat->state() == QSshChannel::Open; }));
    EXPECT_EQ(cat->write(QByteArray(4 * 1024 * 1024 + 1, 'x')), -1);
    EXPECT_EQ(cat->error(), QSshChannel::BufferFull);
    EXPECT_EQ(cat->write("after-error"), 11);
    cat->sendEof();
    ASSERT_TRUE(waitUntil([&] { return cat->state() == QSshChannel::Closed; }));
    EXPECT_EQ(cat->readAll(), QByteArray("after-errorEOF-TAIL"));
    EXPECT_EQ(cat->bytesToWrite(), 0);
}

/** 验证未返回退出码和按信号退出两种情况不被误判为 exitCode=0。 */
TEST_F(QSshIntegration, OptionalExitInformation) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *noStatus = session->createExecChannel();
    auto *signal = session->createExecChannel();
    noStatus->open(QStringLiteral("no-status"));
    signal->open(QStringLiteral("signal"));
    ASSERT_TRUE(
        waitUntil([&] { return noStatus->state() == QSshChannel::Closed && signal->state() == QSshChannel::Closed; }));
    EXPECT_FALSE(noStatus->hasExitCode());
    EXPECT_EQ(noStatus->exitCode(), -1);
    EXPECT_EQ(noStatus->readAll(), QByteArray("tail-without-status"));
    EXPECT_FALSE(signal->hasExitCode());
    EXPECT_EQ(signal->exitSignal(), QStringLiteral("TERM"));
}

/** 验证服务端断开会关闭所有 Channel，且完成通知只发一次。 */
TEST_F(QSshIntegration, RemoteDisconnectClosesChannels) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *waiting = session->createExecChannel();
    auto *disconnect = session->createExecChannel();
    int finished = 0;
    QObject::connect(waiting, &QSshChannel::finished, waiting, [&] { ++finished; });
    waiting->open(QStringLiteral("block"));
    disconnect->open(QStringLiteral("disconnect"));
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(waiting->state(), QSshChannel::Closed);
    EXPECT_EQ(disconnect->state(), QSshChannel::Closed);
    EXPECT_EQ(finished, 1);
}

/** 验证远端不读取输入时发送窗口耗尽不会阻塞其他 Channel，主动关闭有时限。 */
TEST_F(QSshIntegration, SendBackpressureAndCloseTimeout) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *blocked = session->createExecChannel();
    blocked->open(QStringLiteral("block"));
    ASSERT_TRUE(waitUntil([&] { return blocked->state() == QSshChannel::Open; }));
    ASSERT_EQ(blocked->write(QByteArray(4 * 1024 * 1024, 'x')), 4 * 1024 * 1024);
    auto *other = session->createExecChannel();
    other->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] { return other->state() == QSshChannel::Closed; }));
    EXPECT_EQ(other->exitCode(), 7);
    EXPECT_GT(blocked->bytesToWrite(), 0);
    blocked->close();
    ASSERT_TRUE(waitUntil([&] { return blocked->state() == QSshChannel::Closed; }));
    EXPECT_EQ(blocked->error(), QSshChannel::TimeoutError);
    EXPECT_EQ(blocked->bytesToWrite(), 0);
    EXPECT_EQ(session->state(), QSshSession::Ready);
}

/** 验证关闭普通活动 Channel、终止未完成的打开及 client.close() 的终态通知。 */
TEST_F(QSshIntegration, CloseAbortAndClientShutdown) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    auto *active = session->createExecChannel();
    active->open(QStringLiteral("cat"));
    ASSERT_TRUE(waitUntil([&] { return active->state() == QSshChannel::Open; }));
    active->close();
    active->close();
    ASSERT_TRUE(waitUntil([&] { return active->state() == QSshChannel::Closed; }));
    EXPECT_EQ(active->error(), QSshChannel::NoError);
    auto *aborted = session->createExecChannel();
    aborted->open(QStringLiteral("block"));
    aborted->abort();
    ASSERT_TRUE(waitUntil([&] { return aborted->state() == QSshChannel::Closed; }));
    auto *pending = session->createShellChannel();
    pending->open();
    client.close();
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(pending->state(), QSshChannel::Closed);
    EXPECT_EQ(client.createSession(), nullptr);
}

/** 验证 readyRead 槽中删除 Channel 不会使合并通知继续访问已销毁对象。 */
TEST_F(QSshIntegration, DeleteInsideDataCallback) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    ASSERT_EQ(session->state(), QSshSession::Ready);
    QPointer<QSshExecChannel> exec = session->createExecChannel();
    QObject::connect(exec, &QSshChannel::readyRead, exec, [&] { delete exec.data(); });
    exec->open(QStringLiteral("large"));
    ASSERT_TRUE(waitUntil([&] { return exec.isNull(); }));
    auto *other = session->createExecChannel();
    other->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] { return other->state() == QSshChannel::Closed; }));
    EXPECT_EQ(other->exitCode(), 7);
}
