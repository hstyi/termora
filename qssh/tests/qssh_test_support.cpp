#include "qssh_test_support.h"

#include <QCoreApplication>
#include <QElapsedTimer>
#include <QEventLoop>
#include <QThread>
#include <iostream>

using qssh_test::waitUntil;

bool qssh_test::waitUntil(const std::function<bool()> &condition, int milliseconds) {
    QElapsedTimer timer;
    timer.start();
    while (!condition() && timer.elapsed() < milliseconds) {
        QCoreApplication::processEvents(QEventLoop::AllEvents, 10);
        QThread::msleep(1);
    }
    return condition();
}

void QSshIntegration::SetUp() {
    if (QString::fromUtf8(QSSH_TEST_PYTHON).isEmpty()) {
        GTEST_SKIP() << "Set QSSH_TEST_PYTHON to Python with qssh/tests/requirements.txt installed";
    }
    QProcess probe;
    probe.start(QString::fromUtf8(QSSH_TEST_PYTHON), {"-c", "import paramiko"});
    if (!probe.waitForFinished(10000) || probe.exitCode() != 0) {
        GTEST_SKIP() << "Paramiko unavailable; install qssh/tests/requirements.txt into QSSH_TEST_PYTHON";
    }
    ASSERT_TRUE(directory.isValid());
    server.start(QString::fromUtf8(QSSH_TEST_PYTHON), {QString::fromUtf8(QSSH_TEST_SERVER), directory.path()});
    ASSERT_TRUE(server.waitForStarted());
    QByteArray startup;
    ASSERT_TRUE(waitUntil([&] {
        startup += server.readAllStandardOutput();
        return startup.contains('\n') || server.state() == QProcess::NotRunning;
    })) << server.readAllStandardError().constData();
    bool valid = false;
    port = startup.trimmed().toUShort(&valid);
    ASSERT_TRUE(valid) << startup.constData() << server.readAllStandardError().constData();
}

void QSshIntegration::TearDown() {
    if (HasFailure()) {
        std::cerr << server.readAllStandardError().constData();
    }
    server.kill();
    server.waitForFinished(5000);
}

QSshSession::Options QSshIntegration::options(const QString &user) const {
    QSshSession::Options value;
    value.host = QStringLiteral("127.0.0.1");
    value.port = port;
    value.username = user;
    value.password = QStringLiteral("secret");
    value.knownHostsFile = directory.path() + QStringLiteral("/known_hosts");
    value.connectTimeoutMs = 5000;
    value.authenticationTimeoutMs = 10000;
    value.channelTimeoutMs = 2000;
    return value;
}

void QSshIntegration::acceptHost(QSshSession *session) {
    QObject::connect(session, &QSshSession::hostVerificationRequired, session,
                     [session](const QSshSession::HostKeyChallenge &challenge) {
                         EXPECT_EQ(challenge.host, QStringLiteral("127.0.0.1"));
                         EXPECT_TRUE(challenge.fingerprint.startsWith(QStringLiteral("SHA256:")));
                         session->answerHostVerification(challenge.requestId, QSshSession::AcceptOnce);
                     });
}

void QSshIntegration::login(QSshSession *session, const QString &user) {
    acceptHost(session);
    session->connectToHost(options(user));
    ASSERT_TRUE(
        waitUntil([&] { return session->state() == QSshSession::Ready || session->state() == QSshSession::Closed; }));
    ASSERT_EQ(session->state(), QSshSession::Ready)
        << session->errorString().toStdString() << server.readAllStandardError().constData();
}
