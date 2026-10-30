#include "qssh_test_support.h"

#include <QCoreApplication>
#include <QFile>
#include <QPointer>
#include <QThread>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>

using qssh_test::waitUntil;

/** 验证多轮、多问题 OTP，以及过期、重复和错误答案数量不会扰乱当前轮次。 */
TEST_F(QSshIntegration, MultiRoundOtp) {
    QSshClient client;
    auto *session = client.createSession();
    acceptHost(session);
    QList<quint64> ids;
    QObject::connect(session, &QSshSession::authenticationChallenge, session,
                     [&](const QSshSession::AuthChallenge &challenge) {
                         EXPECT_EQ(QThread::currentThread(), session->thread());
                         EXPECT_FALSE(ids.contains(challenge.requestId));
                         ids.append(challenge.requestId);
                         session->answerAuthentication(challenge.requestId + 100, {"stale"});
                         if (ids.size() == 1) {
                             ASSERT_EQ(challenge.prompts.size(), 1);
                             EXPECT_FALSE(challenge.prompts[0].echo);
                             session->answerAuthentication(challenge.requestId, {});
                             session->answerAuthentication(challenge.requestId, {"secret"});
                             session->answerAuthentication(challenge.requestId, {"duplicate"});
                         } else {
                             ASSERT_EQ(challenge.prompts.size(), 2);
                             EXPECT_FALSE(challenge.prompts[0].echo);
                             EXPECT_TRUE(challenge.prompts[1].echo);
                             session->answerAuthentication(ids.first(), {"old"});
                             session->answerAuthentication(challenge.requestId, {"123456", "work"});
                         }
                     });
    session->connectToHost(options(QStringLiteral("otp")));
    ASSERT_TRUE(
        waitUntil([&] { return session->state() == QSshSession::Ready || session->state() == QSshSession::Closed; }));
    EXPECT_EQ(session->state(), QSshSession::Ready) << session->errorString().toStdString();
    EXPECT_EQ(ids.size(), 2);
}

/** 验证等待 OTP 的连接不阻塞同一 Client 上的其他 Session。 */
TEST_F(QSshIntegration, WaitingForOtpDoesNotBlockOtherSessions) {
    QSshClient client;
    auto *waiting = client.createSession();
    acceptHost(waiting);
    quint64 request = 0;
    QObject::connect(waiting, &QSshSession::authenticationChallenge, waiting,
                     [&](const QSshSession::AuthChallenge &challenge) { request = challenge.requestId; });
    waiting->connectToHost(options(QStringLiteral("otp")));
    ASSERT_TRUE(waitUntil([&] { return request != 0; }));
    auto *other = client.createSession();
    login(other);
    ASSERT_EQ(other->state(), QSshSession::Ready);
    auto *exec = other->createExecChannel();
    exec->open(QStringLiteral("report"));
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
    EXPECT_EQ(exec->exitCode(), 7);
    waiting->cancelAuthentication(request);
    ASSERT_TRUE(waitUntil([&] { return waiting->state() == QSshSession::Closed; }));
    EXPECT_EQ(waiting->error(), QSshSession::Cancelled);
}

/** 验证加密私钥认证，以及公钥部分成功后继续 OTP 的多因素链。 */
TEST_F(QSshIntegration, PublicKeyAndPartialAuthentication) {
    QSshClient client;
    for (const auto &user : {QStringLiteral("publickey"), QStringLiteral("publickeyotp")}) {
        auto *session = client.createSession();
        acceptHost(session);
        int challenges = 0;
        QObject::connect(session, &QSshSession::authenticationChallenge, session,
                         [session, &challenges](const QSshSession::AuthChallenge &challenge) {
                             ++challenges;
                             session->answerAuthentication(challenge.requestId, {"123456", "work"});
                         });
        auto configuration = options(user);
        configuration.privateKeyFile = directory.path() + QStringLiteral("/user_key");
        configuration.privateKeyPassphrase = QStringLiteral("key-passphrase");
        session->connectToHost(configuration);
        ASSERT_TRUE(waitUntil(
            [&] { return session->state() == QSshSession::Ready || session->state() == QSshSession::Closed; }));
        ASSERT_EQ(session->state(), QSshSession::Ready) << session->errorString().toStdString();
        EXPECT_EQ(challenges, user == QStringLiteral("publickeyotp") ? 1 : 0);
        delete session;
    }
}

/** 验证不要求输入的通知轮次自动继续，应用无需提交空答案。 */
TEST_F(QSshIntegration, ZeroPromptChallenge) {
    QSshClient client;
    auto *session = client.createSession();
    acceptHost(session);
    int notices = 0;
    int completed = 0;
    QObject::connect(session, &QSshSession::authenticationChallenge, session,
                     [&](const QSshSession::AuthChallenge &challenge) {
                         EXPECT_TRUE(challenge.prompts.isEmpty());
                         EXPECT_EQ(challenge.instruction, QStringLiteral("No input required"));
                         ++notices;
                     });
    QObject::connect(session, &QSshSession::interactionFinished, session, [&](quint64) { ++completed; });
    session->connectToHost(options(QStringLiteral("zero")));
    ASSERT_TRUE(
        waitUntil([&] { return session->state() == QSshSession::Ready || session->state() == QSshSession::Closed; }));
    EXPECT_EQ(session->state(), QSshSession::Ready) << session->errorString().toStdString();
    EXPECT_EQ(notices, 1);
    EXPECT_EQ(completed, 2); // 主机确认与零问题认证各完成一次。
}

/** 验证主机确认可保存，后续连接无需重复询问；显式拒绝不会继续认证。 */
TEST_F(QSshIntegration, KnownHostsSaveAndReject) {
    QSshClient client;
    auto *first = client.createSession();
    QObject::connect(first, &QSshSession::hostVerificationRequired, first,
                     [first](const QSshSession::HostKeyChallenge &challenge) {
                         first->answerHostVerification(challenge.requestId, QSshSession::AcceptAndSave);
                     });
    first->connectToHost(options());
    ASSERT_TRUE(
        waitUntil([&] { return first->state() == QSshSession::Ready || first->state() == QSshSession::Closed; }));
    ASSERT_EQ(first->state(), QSshSession::Ready) << first->errorString().toStdString();
    auto *second = client.createSession();
    int prompts = 0;
    QObject::connect(second, &QSshSession::hostVerificationRequired, second,
                     [&](const QSshSession::HostKeyChallenge &) { ++prompts; });
    second->connectToHost(options());
    ASSERT_TRUE(
        waitUntil([&] { return second->state() == QSshSession::Ready || second->state() == QSshSession::Closed; }));
    EXPECT_EQ(second->state(), QSshSession::Ready) << second->errorString().toStdString();
    EXPECT_EQ(prompts, 0);

    auto *rejected = client.createSession();
    auto fresh = options();
    fresh.knownHostsFile += QStringLiteral("-fresh");
    QObject::connect(rejected, &QSshSession::hostVerificationRequired, rejected,
                     [rejected](const QSshSession::HostKeyChallenge &challenge) {
                         rejected->answerHostVerification(challenge.requestId, QSshSession::RejectHost);
                     });
    rejected->connectToHost(fresh);
    ASSERT_TRUE(waitUntil([&] { return rejected->state() == QSshSession::Closed; }));
    EXPECT_EQ(rejected->error(), QSshSession::HostKeyError);
}

/** 验证 known_hosts 中相同算法的不同密钥会明确报告变更。 */
TEST_F(QSshIntegration, ChangedHostKey) {
    QFile publicKey(directory.path() + QStringLiteral("/user_key.pub"));
    ASSERT_TRUE(publicKey.open(QIODevice::ReadOnly));
    QFile knownHosts(options().knownHostsFile);
    ASSERT_TRUE(knownHosts.open(QIODevice::WriteOnly));
    knownHosts.write("[127.0.0.1]:" + QByteArray::number(port) + " " + publicKey.readAll());
    knownHosts.close();
    QSshClient client;
    auto *session = client.createSession();
    bool changed = false;
    QObject::connect(session, &QSshSession::hostVerificationRequired, session,
                     [&](const QSshSession::HostKeyChallenge &challenge) {
                         changed = challenge.status == QSshSession::ChangedHostKey;
                         session->answerHostVerification(challenge.requestId, QSshSession::RejectHost);
                     });
    session->connectToHost(options());
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_TRUE(changed);
    EXPECT_EQ(session->error(), QSshSession::HostKeyError);
}

/** 验证等待用户回答时的认证时限，以及关闭后旧回答不会复活连接。 */
TEST_F(QSshIntegration, AuthenticationTimeoutInvalidatesChallenge) {
    QSshClient client;
    auto *session = client.createSession();
    acceptHost(session);
    quint64 request = 0;
    QList<quint64> completed;
    QObject::connect(session, &QSshSession::authenticationChallenge, session,
                     [&](const QSshSession::AuthChallenge &challenge) { request = challenge.requestId; });
    QObject::connect(session, &QSshSession::interactionFinished, session, [&](quint64 id) { completed.append(id); });
    auto configuration = options(QStringLiteral("otp"));
    configuration.authenticationTimeoutMs = 1000;
    session->connectToHost(configuration);
    ASSERT_TRUE(waitUntil([&] { return request != 0; }));
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(session->error(), QSshSession::TimeoutError);
    EXPECT_TRUE(completed.contains(request));
    session->answerAuthentication(request, {"secret"});
    session->connectToHost(options());
    QCoreApplication::processEvents();
    EXPECT_EQ(session->state(), QSshSession::Closed);
}

/** 验证密码被拒绝时不会报告 ready，也不会无限尝试同一因素。 */
TEST_F(QSshIntegration, AuthenticationDenied) {
    QSshClient client;
    auto *session = client.createSession();
    acceptHost(session);
    auto configuration = options();
    configuration.password = QStringLiteral("wrong-password");
    session->connectToHost(configuration);
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(session->error(), QSshSession::AuthenticationError);
}

/** 验证等待 OTP 时直接删除前端对象，迟到回调不会访问已销毁的私有实现。 */
TEST_F(QSshIntegration, DeleteInsideAuthenticationCallback) {
    QSshClient client;
    QPointer<QSshSession> session = client.createSession();
    acceptHost(session);
    QObject::connect(session, &QSshSession::authenticationChallenge, session,
                     [&](const QSshSession::AuthChallenge &) { delete session.data(); });
    session->connectToHost(options(QStringLiteral("otp")));
    ASSERT_TRUE(waitUntil([&] { return session.isNull(); }));
    auto *other = client.createSession();
    login(other);
    EXPECT_EQ(other->state(), QSshSession::Ready);
}
