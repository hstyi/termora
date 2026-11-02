#include "qssh_test_support.h"

#include <QMap>
#include <qssh/qsshclient.h>
#include <qssh/qsshsftpsession.h>

using qssh_test::waitUntil;

/** 服务端攒够两个请求后逆序处理；串行实现无法通过，CLOSE 也必须等写入完成。 */
TEST_F(QSshIntegration, SftpPipelineSendsBeforeRepliesAndRoutesOutOfOrder) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    QMap<quint64, quint64> handles;
    QMap<quint64, QByteArray> data;
    QList<quint64> completed;
    QObject::connect(sftp, &QSshSftpSession::fileOpened, sftp, [&](quint64 id, quint64 h) { handles[id] = h; });
    QObject::connect(sftp, &QSshSftpSession::dataRead, sftp,
                     [&](quint64 id, const QByteArray &bytes) { data[id] = bytes; });
    QObject::connect(sftp, &QSshSftpSession::requestFinished, sftp, [&](quint64 id) { completed.append(id); });
    QObject::connect(sftp, &QSshSftpSession::requestFailed, sftp,
                     [](quint64, auto, const QString &message) { ADD_FAILURE() << message.toStdString(); });
    const auto opened = sftp->openFile("/pipeline-data",
                                       QSshSftpSession::Read | QSshSftpSession::Write | QSshSftpSession::Create);
    ASSERT_TRUE(waitUntil([&] { return handles.contains(opened); }));
    const auto handle = handles[opened];
    const auto first = sftp->writeFile(handle, 0, QByteArray(65536, 'a'));
    const auto second = sftp->writeFile(handle, 65536, QByteArray(65536, 'b'));
    const auto close = sftp->closeFile(handle);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(close); }));
    EXPECT_LT(completed.indexOf(second), completed.indexOf(first));
    EXPECT_GT(completed.indexOf(close), completed.indexOf(first));
    const auto reopened = sftp->openFile("/pipeline-data");
    ASSERT_TRUE(waitUntil([&] { return handles.contains(reopened); }));
    const auto left = sftp->readFile(handles[reopened], 0);
    const auto right = sftp->readFile(handles[reopened], 65536);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(left) && completed.contains(right); }));
    EXPECT_EQ(data[left], QByteArray(65536, 'a'));
    EXPECT_EQ(data[right], QByteArray(65536, 'b'));
    EXPECT_LT(completed.indexOf(right), completed.indexOf(left));
}

/** 重叠写入、写后读及 Append 保留提交语义，不把所有读写都无条件并行。 */
TEST_F(QSshIntegration, SftpPipelinePreservesConflictsAndAppendOrder) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    quint64 handle = 0;
    QByteArray content;
    QList<quint64> completed;
    QObject::connect(sftp, &QSshSftpSession::fileOpened, sftp, [&](quint64, quint64 h) { handle = h; });
    QObject::connect(sftp, &QSshSftpSession::dataRead, sftp, [&](quint64, const QByteArray &d) { content = d; });
    QObject::connect(sftp, &QSshSftpSession::requestFinished, sftp, [&](quint64 id) { completed.append(id); });
    sftp->openFile("/conflict", QSshSftpSession::Read | QSshSftpSession::Write | QSshSftpSession::Create);
    ASSERT_TRUE(waitUntil([&] { return handle != 0; }));
    sftp->writeFile(handle, 0, "aaaaaa");
    sftp->writeFile(handle, 2, "BBBB");
    auto read = sftp->readFile(handle, 0, 6);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(read); }));
    EXPECT_EQ(content, "aaBBBB");
    auto close = sftp->closeFile(handle);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(close); }));
    handle = 0;
    sftp->openFile("/conflict", QSshSftpSession::Read | QSshSftpSession::Write | QSshSftpSession::Append);
    ASSERT_TRUE(waitUntil([&] { return handle != 0; }));
    sftp->writeFile(handle, 0, "one");
    sftp->writeFile(handle, 0, "two");
    read = sftp->readFile(handle, 0, 12);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(read); }));
    EXPECT_EQ(content, "aaBBBBonetwo");
}
