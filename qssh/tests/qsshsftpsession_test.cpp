#include "qssh_test_support.h"

#include <QCoreApplication>
#include <QFile>
#include <QMap>
#include <QPointer>
#include <QSet>
#include <QThread>
#include <limits>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>
#include <qssh/qsshsftpsession.h>

using qssh_test::waitUntil;
using Sftp = QSshSftpSession;

namespace {
    /** 收集一个子系统的异步结果；测试只在 Qt 线程读取，不访问 Private。 */
    struct Results {
        /** 连接 sftp 的公开信号，确认结果始终由 Qt 所属线程交付。 */
        explicit Results(Sftp *sftp) {
            QObject::connect(sftp, &Sftp::requestFinished, sftp, [this, sftp](quint64 id) {
                EXPECT_EQ(QThread::currentThread(), sftp->thread());
                EXPECT_FALSE(finished.contains(id));
                EXPECT_FALSE(failures.contains(id));
                finished.insert(id);
            });
            QObject::connect(sftp, &Sftp::requestFailed, sftp, [this](quint64 id, Sftp::Error error, const QString &) {
                EXPECT_FALSE(finished.contains(id));
                EXPECT_FALSE(failures.contains(id));
                failures.insert(id, error);
            });
            QObject::connect(sftp, &Sftp::fileOpened, sftp,
                             [this](quint64 id, quint64 handle) { handles.insert(id, handle); });
            QObject::connect(sftp, &Sftp::dataRead, sftp,
                             [this](quint64 id, const QByteArray &data) { reads.insert(id, data); });
            QObject::connect(sftp, &Sftp::fileInfoAvailable, sftp,
                             [this](quint64 id, const Sftp::FileInfo &info) { infos.insert(id, info); });
            QObject::connect(sftp, &Sftp::pathResolved, sftp,
                             [this](quint64 id, const QString &path) { paths.insert(id, path); });
            QObject::connect(sftp, &Sftp::directoryEntries, sftp,
                             [this](quint64 id, const QList<Sftp::FileInfo> &batch) {
                                 entries[id].append(batch);
                                 ++batches[id];
                             });
        }

        /** 等待 id 的唯一终结通知，成功返回 true；拒绝提交的零编号不会被当成完成。 */
        bool success(quint64 id) {
            return id && waitUntil([&] { return finished.contains(id) || failures.contains(id); }) &&
                   finished.contains(id);
        }

        QSet<quint64> finished;                       ///< 成功结束的请求。
        QMap<quint64, Sftp::Error> failures;          ///< 失败请求及类别。
        QMap<quint64, quint64> handles;               ///< 打开请求返回的句柄。
        QMap<quint64, QByteArray> reads;              ///< 读取请求返回的数据。
        QMap<quint64, Sftp::FileInfo> infos;          ///< stat/lstat 属性。
        QMap<quint64, QString> paths;                 ///< realpath/readlink 路径。
        QMap<quint64, QList<Sftp::FileInfo>> entries; ///< 仅测试主动汇总整个目录。
        QMap<quint64, int> batches;                   ///< 每次目录遍历实际交付的批次数。
    };

    /** 等待已 open 的 sftp 协商成功或报告终止，避免错误时等到测试整体时限。 */
    bool ready(Sftp *sftp) {
        return waitUntil([&] { return sftp->state() == Sftp::Ready || sftp->state() == Sftp::Closed; }) &&
               sftp->state() == Sftp::Ready;
    }
} // namespace

/** 不依赖 Python，验证创建关系、未就绪拒绝请求以及关闭终态。 */
TEST(QSshSftp, RequiresReadySessionAndHasTerminalClose) {
    QSshClient client;
    auto *session = client.createSession();
    auto *sftp = session->createSftpSession();
    ASSERT_NE(sftp, nullptr);
    EXPECT_EQ(sftp->parent(), session);
    EXPECT_EQ(sftp->state(), Sftp::Idle);
    EXPECT_EQ(sftp->stat("/"), 0u);
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == Sftp::Closed; }));
    EXPECT_EQ(sftp->error(), Sftp::InvalidState);
    sftp->open();
    EXPECT_EQ(sftp->state(), Sftp::Closed);
    delete sftp;
    auto *unused = session->createSftpSession();
    unused->close();
    ASSERT_TRUE(waitUntil([&] { return unused->state() == Sftp::Closed; }));
    EXPECT_EQ(unused->error(), Sftp::NoError);
}

/** 覆盖分片响应、二进制内容、排队分块写入、EOF 及 64 位稀疏文件偏移。 */
TEST_F(QSshIntegration, SftpBinaryBlocksAndLargeOffsets) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    auto *sftp = session->createSftpSession();
    Results results(sftp);
    sftp->open();
    ASSERT_TRUE(ready(sftp)) << sftp->errorString().toStdString();
    const auto opened = sftp->openFile("/binary", Sftp::Read | Sftp::Write | Sftp::Create | Sftp::Truncate);
    ASSERT_TRUE(results.success(opened));
    const auto handle = results.handles.value(opened);
    ASSERT_NE(handle, 0u);
    QByteArray content(3 * 65536 + 27, Qt::Uninitialized);
    for (qsizetype i = 0; i < content.size(); ++i) {
        content.data()[i] = static_cast<char>((i * 37) & 255);
    }
    QList<quint64> writes;
    for (qsizetype offset = 0; offset < content.size(); offset += 65536) {
        writes.append(sftp->writeFile(handle, offset, content.mid(offset, 65536)));
    }
    for (const auto id : writes) {
        ASSERT_TRUE(results.success(id));
    }
    QByteArray received;
    while (received.size() < content.size()) {
        const auto id = sftp->readFile(handle, received.size());
        ASSERT_TRUE(results.success(id));
        ASSERT_FALSE(results.reads.value(id).isEmpty());
        received.append(results.reads.value(id));
    }
    EXPECT_EQ(received, content);
    const auto eof = sftp->readFile(handle, content.size());
    ASSERT_TRUE(results.success(eof));
    EXPECT_TRUE(results.reads.contains(eof));
    EXPECT_TRUE(results.reads.value(eof).isEmpty());

    constexpr quint64 largeOffset = (quint64(1) << 32) + 7;
    ASSERT_TRUE(results.success(sftp->writeFile(handle, largeOffset, QByteArray("\0\xffx", 3))));
    const auto sparseRead = sftp->readFile(handle, largeOffset, 3);
    ASSERT_TRUE(results.success(sparseRead));
    EXPECT_EQ(results.reads.value(sparseRead), QByteArray("\0\xffx", 3));
    ASSERT_TRUE(results.success(sftp->closeFile(handle)));
    const auto info = sftp->stat("/binary");
    ASSERT_TRUE(results.success(info));
    EXPECT_TRUE(results.infos[info].attributes.testFlag(Sftp::Size));
    EXPECT_EQ(results.infos[info].size, largeOffset + 3);
    const auto closedRead = sftp->readFile(handle, 0);
    ASSERT_TRUE(waitUntil([&] { return results.failures.contains(closedRead); }));
    EXPECT_EQ(results.failures.value(closedRead), Sftp::InvalidState);
    EXPECT_EQ(sftp->state(), Sftp::Ready);
}

/** 验证多批目录、UTF-8 路径、属性有效位、权限、链接和文件／目录删除。 */
TEST_F(QSshIntegration, SftpDirectoriesAttributesAndLinks) {
    for (int i = 0; i < 40; ++i) {
        QFile file(directory.path() + QStringLiteral("/sftp-root/entry-%1").arg(i));
        ASSERT_TRUE(file.open(QIODevice::WriteOnly));
        file.write("fixture");
    }
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    auto *sftp = session->createSftpSession();
    Results results(sftp);
    sftp->open();
    ASSERT_TRUE(ready(sftp));
    const auto listing = sftp->listDirectory("/");
    ASSERT_TRUE(results.success(listing));
    EXPECT_EQ(results.entries.value(listing).size(), 40);
    EXPECT_GE(results.batches.value(listing), 3);
    ASSERT_TRUE(results.success(sftp->makeDirectory(
        QStringLiteral("/资料"), QFileDevice::ReadOwner | QFileDevice::WriteOwner | QFileDevice::ExeOwner)));
    const auto opened = sftp->openFile(QStringLiteral("/资料/文件"), Sftp::Write | Sftp::Create | Sftp::Exclusive,
                                       0600);
    ASSERT_TRUE(results.success(opened));
    const auto handle = results.handles.value(opened);
    ASSERT_TRUE(results.success(sftp->writeFile(handle, 0, "content")));
    ASSERT_TRUE(results.success(sftp->closeFile(handle)));
    ASSERT_TRUE(results.success(sftp->setPermissions(
        QStringLiteral("/资料/文件"), QFileDevice::ReadOwner | QFileDevice::WriteOwner | QFileDevice::ReadGroup)));
    ASSERT_TRUE(results.success(sftp->rename(QStringLiteral("/资料/文件"), QStringLiteral("/资料/renamed"))));
    const auto info = sftp->stat(QStringLiteral("/资料/renamed"));
    ASSERT_TRUE(results.success(info));
    EXPECT_EQ(results.infos[info].size, 7u);
    EXPECT_EQ(results.infos[info].mode & 07777, 0640u);
    EXPECT_EQ(results.infos[info].permissions,
              QFileDevice::ReadOwner | QFileDevice::WriteOwner | QFileDevice::ReadGroup);
    EXPECT_TRUE(results.infos[info].attributes.testFlag(Sftp::Times));
    EXPECT_TRUE(results.infos[info].attributes.testFlag(Sftp::UserAndGroup));
    ASSERT_TRUE(
        results.success(sftp->createSymbolicLink(QStringLiteral("/资料/renamed"), QStringLiteral("/资料/link"))));
    const auto link = sftp->readLink(QStringLiteral("/资料/link"));
    ASSERT_TRUE(results.success(link));
    EXPECT_EQ(results.paths.value(link), QStringLiteral("/资料/renamed"));
    const auto linkInfo = sftp->stat(QStringLiteral("/资料/link"), false);
    ASSERT_TRUE(results.success(linkInfo));
    EXPECT_EQ(results.infos[linkInfo].mode & 0170000, 0120000u);
    const auto followed = sftp->stat(QStringLiteral("/资料/link"));
    ASSERT_TRUE(results.success(followed));
    EXPECT_EQ(results.infos[followed].mode & 0170000, 0100000u);
    const auto canonical = sftp->canonicalPath(QStringLiteral("/资料/./renamed"));
    ASSERT_TRUE(results.success(canonical));
    EXPECT_EQ(results.paths.value(canonical), QStringLiteral("/资料/renamed"));
    ASSERT_TRUE(results.success(sftp->removeFile(QStringLiteral("/资料/link"))));
    ASSERT_TRUE(results.success(sftp->removeFile(QStringLiteral("/资料/renamed"))));
    ASSERT_TRUE(results.success(sftp->removeDirectory(QStringLiteral("/资料"))));
}

/** 远端状态错误只结束对应请求，后续请求继续；本地参数错误不占用编号和队列。 */
TEST_F(QSshIntegration, SftpErrorsAndArgumentValidation) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    auto *sftp = session->createSftpSession();
    Results results(sftp);
    sftp->open();
    ASSERT_TRUE(ready(sftp));
    EXPECT_EQ(sftp->openFile("/invalid", Sftp::Truncate), 0u);
    EXPECT_EQ(sftp->openFile("/invalid", Sftp::Write | Sftp::Exclusive), 0u);
    EXPECT_EQ(sftp->stat(QString::fromLatin1("nul\0path", 8)), 0u);
    EXPECT_EQ(sftp->stat(QString(33000, QChar('x'))), 0u);
    EXPECT_EQ(sftp->rename("/", ""), 0u);
    EXPECT_EQ(sftp->readFile(1, 0, 65537), 0u);
    EXPECT_EQ(sftp->writeFile(1, 0, QByteArray(65537, 'x')), 0u);
    EXPECT_EQ(sftp->readFile(1, std::numeric_limits<quint64>::max(), 1), 0u);
    EXPECT_EQ(sftp->makeDirectory("/invalid", 0100000), 0u);
    const auto missing = sftp->stat("/missing");
    const auto denied = sftp->stat("/denied");
    const auto fragmented = sftp->stat("/fragmented");
    const auto usable = sftp->stat("/");
    ASSERT_TRUE(results.success(usable));
    EXPECT_TRUE(results.finished.contains(fragmented));
    EXPECT_EQ(results.failures.value(missing), Sftp::NoSuchFile);
    EXPECT_EQ(results.failures.value(denied), Sftp::PermissionDenied);
    EXPECT_EQ(sftp->error(), Sftp::NoError);
    EXPECT_EQ(sftp->state(), Sftp::Ready);
}

/** SFTP 等待慢元数据时，同连接的 exec 和另一个 SFTP 子系统仍能推进。 */
TEST_F(QSshIntegration, SftpSharesWorkerWithoutBlockingOtherChannels) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    auto *first = session->createSftpSession();
    auto *second = session->createSftpSession();
    Results firstResults(first);
    Results secondResults(second);
    first->open();
    second->open();
    ASSERT_TRUE(ready(first));
    ASSERT_TRUE(ready(second));
    const auto slow = first->stat("/slow");
    const auto fast = second->stat("/");
    auto *exec = session->createExecChannel();
    exec->open("echo concurrent");
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }, 500));
    EXPECT_EQ(exec->readAll(), "echo concurrent");
    ASSERT_TRUE(secondResults.success(fast));
    EXPECT_FALSE(firstResults.finished.contains(slow));
    ASSERT_TRUE(firstResults.success(slow));
    first->close();
    ASSERT_TRUE(waitUntil([&] { return first->state() == Sftp::Closed; }));
    EXPECT_EQ(second->state(), Sftp::Ready);
    EXPECT_EQ(session->state(), QSshSession::Ready);
}

/** 慢请求后可排队超过 64 个请求；写入仍按字节预算控制，close 逐一报告取消。 */
TEST_F(QSshIntegration, SftpWriteBudgetAndQueuedCancellation) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    auto *sftp = session->createSftpSession();
    Results results(sftp);
    sftp->open();
    ASSERT_TRUE(ready(sftp));
    QList<quint64> ids = {sftp->stat("/stall")};
    for (int i = 0; i < 128; ++i) {
        ids.append(sftp->stat("/"));
    }
    for (int i = 0; i < 64; ++i) {
        ids.append(sftp->writeFile(1, 0, QByteArray(65536, 'x')));
    }
    EXPECT_EQ(sftp->writeFile(1, 0, "x"), 0u);
    ids.append(sftp->stat("/"));
    for (const auto id : ids) {
        EXPECT_NE(id, 0u);
    }
    sftp->close();
    EXPECT_EQ(sftp->stat("/"), 0u);
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == Sftp::Closed; }));
    EXPECT_EQ(results.failures.size(), ids.size());
    EXPECT_TRUE(results.finished.isEmpty());
    for (const auto id : ids) {
        EXPECT_EQ(results.failures.value(id), Sftp::Cancelled);
    }
    EXPECT_EQ(session->state(), QSshSession::Ready);
}

/** 超时关闭该 SFTP 子系统并终止所有挂起请求，SSH 连接仍保持可用。 */
TEST_F(QSshIntegration, SftpRequestTimeoutAndParentClose) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    auto *sftp = session->createSftpSession();
    Results results(sftp);
    Sftp::Options configuration;
    configuration.requestTimeoutMs = 200;
    sftp->open(configuration);
    ASSERT_TRUE(ready(sftp));
    const auto stalled = sftp->stat("/stall");
    const auto queued = sftp->stat("/");
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == Sftp::Closed; }));
    EXPECT_EQ(sftp->error(), Sftp::TimeoutError);
    EXPECT_EQ(results.failures.value(stalled), Sftp::TimeoutError);
    EXPECT_EQ(results.failures.value(queued), Sftp::TimeoutError);
    EXPECT_EQ(session->state(), QSshSession::Ready);
    auto *second = session->createSftpSession();
    Results secondResults(second);
    second->open();
    ASSERT_TRUE(ready(second));
    const auto pending = second->stat("/stall");
    session->close();
    ASSERT_TRUE(waitUntil([&] { return session->state() == QSshSession::Closed; }));
    EXPECT_EQ(second->state(), Sftp::Closed);
    EXPECT_EQ(second->error(), Sftp::SessionClosed);
    EXPECT_EQ(secondResults.failures.value(pending), Sftp::SessionClosed);
    EXPECT_EQ(session->createSftpSession(), nullptr);
}

/** 错误编号、超长、截断及错误类型响应都仅关闭所属子系统，不影响连接或其它子系统。 */
TEST_F(QSshIntegration, SftpRejectsMalformedResponses) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    for (const auto &path : {"/bad-id", "/oversized", "/truncated", "/wrong-type"}) {
        auto *sftp = session->createSftpSession();
        Results results(sftp);
        sftp->open();
        ASSERT_TRUE(ready(sftp));
        const auto request = sftp->stat(path);
        ASSERT_NE(request, 0u);
        ASSERT_TRUE(waitUntil([&] { return sftp->state() == Sftp::Closed; }));
        EXPECT_EQ(sftp->error(), Sftp::ProtocolError) << path;
        EXPECT_EQ(results.failures.value(request), Sftp::ProtocolError);
        EXPECT_EQ(session->state(), QSshSession::Ready);
        delete sftp;
    }
    auto *usable = session->createSftpSession();
    Results results(usable);
    usable->open();
    ASSERT_TRUE(ready(usable));
    ASSERT_TRUE(results.success(usable->stat("/")));
}

/** 覆盖尚未启动、握手挂起、请求挂起和结果信号内删除的生命周期。 */
TEST_F(QSshIntegration, SftpDestructionDuringCoroutinesAndSignals) {
    QSshClient client;
    auto *session = client.createSession();
    login(session);
    for (int i = 0; i < 10; ++i) {
        auto *sftp = session->createSftpSession();
        sftp->open();
        delete sftp;
    }
    for (int i = 0; i < 3; ++i) {
        auto *sftp = session->createSftpSession();
        sftp->open();
        ASSERT_TRUE(ready(sftp));
        ASSERT_NE(sftp->stat("/stall"), 0u);
        delete sftp;
    }
    QPointer<Sftp> fromSignal = session->createSftpSession();
    QObject::connect(fromSignal, &Sftp::fileOpened, fromSignal,
                     [sftp = fromSignal.data()](quint64, quint64) { delete sftp; });
    fromSignal->open();
    ASSERT_TRUE(ready(fromSignal));
    ASSERT_NE(fromSignal->openFile("/deleted", Sftp::Write | Sftp::Create), 0u);
    ASSERT_TRUE(waitUntil([&] { return fromSignal.isNull(); }));
    QPointer<Sftp> fromDirectory = session->createSftpSession();
    QObject::connect(fromDirectory, &Sftp::directoryEntries, fromDirectory,
                     [sftp = fromDirectory.data()](quint64, const QList<Sftp::FileInfo> &) { delete sftp; });
    fromDirectory->open();
    ASSERT_TRUE(ready(fromDirectory));
    ASSERT_NE(fromDirectory->listDirectory("/"), 0u);
    ASSERT_TRUE(waitUntil([&] { return fromDirectory.isNull(); }));
    auto *owned = session->createSftpSession();
    owned->open();
    ASSERT_TRUE(ready(owned));
    ASSERT_NE(owned->stat("/stall"), 0u);
    delete session;
    QCoreApplication::processEvents();
}
