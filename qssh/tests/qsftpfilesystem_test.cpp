#include "qssh_test_support.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QPointer>
#include <QSet>
#include <QThread>
#include <qssh/qsftpfilesystem.h>
#include <qssh/qsshclient.h>
#include <qssh/qsshsftpsession.h>
#include <qvfs/qlocalfilesystem.h>
#include <qvfs/qvirtualfile.h>

using qssh_test::waitUntil;

namespace {
    bool ready(QSshSftpSession *session) {
        session->open();
        return waitUntil([&] {
                   return session->state() == QSshSftpSession::Ready || session->state() == QSshSftpSession::Closed;
               }) &&
               session->state() == QSshSftpSession::Ready;
    }

    bool completed(QVirtualJob *job) {
        return waitUntil([&] { return job->isFinished(); });
    }

    void write(const QString &path, const QByteArray &data = {}) {
        QFile file(path);
        ASSERT_TRUE(file.open(QIODevice::WriteOnly));
        ASSERT_EQ(file.write(data), data.size());
    }
} // namespace

TEST(QSftpFileSystem, ReferencesUseRemotePathRulesAndNotReadyIsAsynchronous) {
    QSshClient client;
    auto *session = client.createSession()->createSftpSession();
    QSftpFileSystem fs(session);
    auto *file = fs.file("/a/../b//c");
    ASSERT_NE(file, nullptr);
    EXPECT_EQ(file->path(), "/b/c");
    EXPECT_EQ(file->parentFile()->path(), "/b");
    EXPECT_EQ(file->child("back\\slash")->name(), "back\\slash");
    EXPECT_EQ(file->child("../bad"), nullptr);
    EXPECT_EQ(fs.file("relative"), nullptr);
    EXPECT_EQ(fs.file("C:/local"), nullptr);
    EXPECT_EQ(fs.file("/")->parentFile(), nullptr);
    auto *job = file->stat();
    auto *roots = fs.roots();
    EXPECT_EQ(job->state(), QVirtualJob::Pending);
    EXPECT_EQ(roots->state(), QVirtualJob::Pending);
    ASSERT_TRUE(completed(job));
    ASSERT_TRUE(completed(roots));
    EXPECT_EQ(job->error(), QVirtualJob::NotReady);
    EXPECT_EQ(roots->error(), QVirtualJob::NotReady);
}

TEST_F(QSshIntegration, VirtualSftpRootsStatAndBatchedListing) {
    const auto rootPath = directory.filePath("sftp-root");
    write(rootPath + "/binary", QByteArray("a\0b", 3));
    write(rootPath + "/.hidden");
    for (int i = 0; i < 19; ++i) {
        write(rootPath + "/entry" + QString::number(i));
    }
    ASSERT_TRUE(QFile::link(rootPath + "/binary", rootPath + "/link"));
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    QSftpFileSystem fs(session);
    auto *roots = fs.roots();
    QList<QVirtualFileInfo> rootEntries;
    QObject::connect(roots, &QVirtualListJob::entriesReady, roots,
                     [&](const auto &entries) { rootEntries.append(entries); });
    EXPECT_EQ(roots->state(), QVirtualJob::Pending);
    ASSERT_TRUE(completed(roots));
    ASSERT_EQ(rootEntries.size(), 1);
    EXPECT_EQ(rootEntries[0].path, "/");
    EXPECT_TRUE(rootEntries[0].isRoot);
    auto *stat = fs.file("/binary")->stat();
    auto *link = fs.stat("/link");
    auto *missing = fs.stat("/missing");
    auto *invalid = fs.stat("relative");
    QVirtualListOptions options;
    options.batchSize = 3;
    options.includeHidden = false;
    auto *listing = fs.file("/")->list(options);
    QSet<QString> names;
    QObject::connect(listing, &QVirtualListJob::entriesReady, listing, [&](const auto &entries) {
        EXPECT_EQ(QThread::currentThread(), fs.thread());
        EXPECT_LE(entries.size(), 3);
        for (const auto &info : entries) {
            EXPECT_EQ(info.path, "/" + info.name);
            names.insert(info.name);
        }
    });
    ASSERT_TRUE(completed(stat));
    ASSERT_TRUE(completed(link));
    ASSERT_TRUE(completed(missing));
    ASSERT_TRUE(completed(invalid));
    ASSERT_TRUE(completed(listing));
    EXPECT_EQ(stat->error(), QVirtualJob::NoError);
    EXPECT_EQ(stat->info().size, 3u);
    EXPECT_EQ(stat->info().type, QVirtualFileInfo::File);
    EXPECT_TRUE(stat->info().attributes.testFlag(QVirtualFileInfo::Size));
    EXPECT_EQ(link->info().type, QVirtualFileInfo::SymbolicLink);
    EXPECT_EQ(missing->error(), QVirtualJob::NotFound);
    EXPECT_EQ(missing->backendError(), QSshSftpSession::NoSuchFile);
    EXPECT_EQ(invalid->error(), QVirtualJob::InvalidPath);
    EXPECT_EQ(listing->error(), QVirtualJob::NoError);
    EXPECT_EQ(listing->entriesCount(), 21u);
    EXPECT_EQ(names.size(), 21);
    EXPECT_FALSE(names.contains(".hidden"));
}

TEST_F(QSshIntegration, VirtualSftpCancellationDoesNotCloseSharedSession) {
    const auto rootPath = directory.filePath("sftp-root");
    for (int i = 0; i < 40; ++i) {
        write(rootPath + "/entry" + QString::number(i));
    }
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    QSftpFileSystem fs(session);
    auto *listing = fs.list("/", {1, true});
    int batches = 0;
    int finished = 0;
    QObject::connect(listing, &QVirtualListJob::entriesReady, listing, [&](const auto &) {
        ++batches;
        listing->cancel();
    });
    QObject::connect(listing, &QVirtualJob::finished, listing, [&] { ++finished; });
    auto *stat = fs.stat("/entry0");
    ASSERT_TRUE(completed(listing));
    ASSERT_TRUE(completed(stat));
    EXPECT_EQ(listing->state(), QVirtualJob::Cancelled);
    EXPECT_EQ(batches, 1);
    EXPECT_EQ(finished, 1);
    EXPECT_EQ(stat->state(), QVirtualJob::Succeeded);
    EXPECT_EQ(session->state(), QSshSftpSession::Ready);
    QPointer<QVirtualListJob> deleted = fs.list("/", {1, true});
    QObject::connect(deleted, &QVirtualListJob::entriesReady, deleted,
                     [&deleted](const auto &) { delete deleted.data(); });
    ASSERT_TRUE(waitUntil([&] { return deleted.isNull(); }));
    auto *after = fs.stat("/entry1");
    ASSERT_TRUE(completed(after));
    EXPECT_EQ(after->state(), QVirtualJob::Succeeded);
}

TEST_F(QSshIntegration, VirtualSftpBorrowingAndSessionDestructionAreSafe) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    delete new QSftpFileSystem(session);
    EXPECT_EQ(session->state(), QSshSftpSession::Ready);
    QSftpFileSystem fs(session);
    auto *stat = fs.stat("/");
    int finished = 0;
    QObject::connect(stat, &QVirtualJob::finished, stat, [&] { ++finished; });
    delete session;
    ASSERT_TRUE(completed(stat));
    EXPECT_EQ(stat->error(), QVirtualJob::FileSystemClosed);
    EXPECT_EQ(finished, 1);
    auto *roots = fs.roots();
    ASSERT_TRUE(completed(roots));
    EXPECT_EQ(roots->error(), QVirtualJob::FileSystemClosed);
}

/** 与原生测试相同，服务端等两个请求并逆序回复；逐块等待的 VFS 实现必然超时。 */
TEST_F(QSshIntegration, VirtualSftpHandlesPipelineAndCloseBarrier) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    QSftpFileSystem fs(session);
    QVirtualOpenOptions options;
    options.access = QVirtualOpenOptions::ReadWrite;
    options.creation = QVirtualOpenOptions::CreateNew;
    auto *open = fs.file("/pipeline-vfs-handles")->open(options);
    EXPECT_EQ(open->state(), QVirtualJob::Pending);
    ASSERT_TRUE(completed(open));
    ASSERT_EQ(open->error(), QVirtualJob::NoError) << open->errorString().toStdString();
    auto *handle = open->takeHandle();
    ASSERT_NE(handle, nullptr);
    delete open;
    QList<int> order;
    auto *left = handle->writeAt(0, QByteArray(65536, 'a'));
    auto *right = handle->writeAt(65536, QByteArray(65536, 'b'));
    QObject::connect(left, &QVirtualJob::finished, left, [&] { order.append(1); });
    QObject::connect(right, &QVirtualJob::finished, right, [&] { order.append(2); });
    auto *close = handle->close();
    ASSERT_TRUE(completed(close));
    EXPECT_EQ(left->error(), QVirtualJob::NoError);
    EXPECT_EQ(right->bytesWritten(), 65536);
    EXPECT_EQ(close->error(), QVirtualJob::NoError);
    ASSERT_EQ(order.size(), 2);
    EXPECT_EQ(order[0], 2);
    auto *readOpen = fs.open("/pipeline-vfs-handles");
    ASSERT_TRUE(completed(readOpen));
    auto *reader = readOpen->takeHandle();
    ASSERT_NE(reader, nullptr);
    auto *a = reader->readAt(0);
    auto *b = reader->readAt(65536);
    ASSERT_TRUE(completed(a));
    ASSERT_TRUE(completed(b));
    EXPECT_EQ(a->data(), QByteArray(65536, 'a'));
    EXPECT_EQ(b->data(), QByteArray(65536, 'b'));
    ASSERT_TRUE(completed(reader->close()));
}

TEST_F(QSshIntegration, VirtualSftpCopyUsesNativeUploadDownloadAndGenericRemotePipeline) {
    const QByteArray payload = QByteArray(65536, 'a') + QByteArray(65536, 'b');
    const auto localPath = directory.filePath("source");
    write(localPath, payload);
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    QSftpFileSystem remote(session);
    QLocalFileSystem local;
    auto *upload = local.file(localPath)->copyTo(remote.file("/pipeline-vfs-copy"));
    ASSERT_TRUE(completed(upload));
    EXPECT_EQ(upload->error(), QVirtualJob::NoError) << upload->errorString().toStdString();
    EXPECT_EQ(upload->bytesTransferred(), payload.size());
    auto *download = remote.file("/pipeline-vfs-copy")->copyTo(local.file(directory.filePath("download")));
    ASSERT_TRUE(completed(download));
    EXPECT_EQ(download->error(), QVirtualJob::NoError) << download->errorString().toStdString();
    QFile result(directory.filePath("download"));
    ASSERT_TRUE(result.open(QIODevice::ReadOnly));
    EXPECT_EQ(result.readAll(), payload);
    auto *copy = remote.file("/pipeline-vfs-copy")->copyTo(remote.file("/pipeline-vfs-second"));
    ASSERT_TRUE(completed(copy));
    EXPECT_EQ(copy->error(), QVirtualJob::NoError) << copy->errorString().toStdString();
    QFile serverCopy(directory.filePath("sftp-root/pipeline-vfs-second"));
    ASSERT_TRUE(serverCopy.open(QIODevice::ReadOnly));
    EXPECT_EQ(serverCopy.readAll(), payload);
    QVirtualTransferOptions atomic;
    atomic.overwrite = true;
    atomic.atomicReplace = true;
    auto *unsupported = local.file(localPath)->copyTo(remote.file("/pipeline-vfs-copy"), atomic);
    ASSERT_TRUE(completed(unsupported));
    EXPECT_EQ(unsupported->error(), QVirtualJob::Unsupported);
    auto *missing = remote.file("/missing")->copyTo(local.file(directory.filePath("missing-download")));
    ASSERT_TRUE(completed(missing));
    EXPECT_EQ(missing->error(), QVirtualJob::NotFound);
    EXPECT_FALSE(QFile::exists(directory.filePath("missing-download")));
}

TEST_F(QSshIntegration, VirtualSftpRemoveRenameAndExplicitCapabilities) {
    const auto root = directory.filePath("sftp-root");
    ASSERT_TRUE(QDir(root).mkpath("tree/child"));
    write(root + "/tree/child/file", "data");
    write(root + "/source", "source");
    ASSERT_TRUE(QFile::link(root + "/source", root + "/tree/link"));
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    QSftpFileSystem fs(session);
    EXPECT_TRUE(fs.capabilities().testFlag(QVirtualFileSystem::RandomWrite));
    EXPECT_FALSE(fs.capabilities().testFlag(QVirtualFileSystem::AtomicReplace));
    auto *source = fs.file("/source");
    auto *renamed = source->renameTo(fs.file("/target"));
    ASSERT_TRUE(completed(renamed));
    EXPECT_EQ(renamed->error(), QVirtualJob::NoError);
    EXPECT_EQ(source->path(), "/source");
    auto *cancel = fs.remove("/target");
    cancel->cancel();
    ASSERT_TRUE(completed(cancel));
    EXPECT_EQ(cancel->state(), QVirtualJob::Cancelled);
    EXPECT_TRUE(QFile::exists(root + "/target"));
    QVirtualRenameOptions atomic;
    atomic.requireAtomic = true;
    auto *unsupported = fs.rename("/target", "/other", atomic);
    ASSERT_TRUE(completed(unsupported));
    EXPECT_EQ(unsupported->error(), QVirtualJob::Unsupported);
    auto *remove = fs.remove("/tree", {true, false});
    ASSERT_TRUE(completed(remove));
    EXPECT_EQ(remove->error(), QVirtualJob::NoError) << remove->errorString().toStdString();
    EXPECT_EQ(remove->affectedCount(), 4u);
    EXPECT_TRUE(QFile::exists(root + "/target"));
    EXPECT_FALSE(QFile::exists(root + "/tree"));
    auto *protectedRoot = fs.remove("/", {true, false});
    ASSERT_TRUE(completed(protectedRoot));
    EXPECT_EQ(protectedRoot->error(), QVirtualJob::InvalidArgument);
}

TEST_F(QSshIntegration, VirtualSftpHandlesOutliveClientAndUnclaimedOpenIsCleanedUp) {
    QObject owner;
    QVirtualFileHandle *handle = nullptr;
    {
        QSshClient client;
        auto *ssh = client.createSession();
        login(ssh);
        auto *session = ssh->createSftpSession();
        ASSERT_TRUE(ready(session));
        QSftpFileSystem fs(session);
        write(directory.filePath("sftp-root/data"), "data");
        auto *open = fs.open("/data");
        ASSERT_TRUE(completed(open));
        handle = open->takeHandle(&owner);
        ASSERT_NE(handle, nullptr);
        auto *unclaimed = fs.open("/data");
        QCoreApplication::processEvents();
        delete unclaimed;
    }
    auto *read = handle->readAt(0);
    ASSERT_TRUE(completed(read));
    EXPECT_EQ(read->error(), QVirtualJob::FileSystemClosed);
    delete handle;
}

TEST_F(QSshIntegration, VirtualSftpMutationCancelWaitsForActualAcknowledgement) {
    write(directory.filePath("sftp-root/source"), "data");
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    QSftpFileSystem fs(session);
    auto *job = fs.rename("/source", "/rename-slow-reply");
    QByteArray output;
    ASSERT_TRUE(waitUntil([&] {
        output += server.readAllStandardOutput();
        return output.contains("SFTP_RENAME_APPLIED");
    }));
    job->cancel();
    EXPECT_TRUE(job->isCancellationRequested());
    EXPECT_FALSE(job->isFinished());
    ASSERT_TRUE(completed(job));
    EXPECT_EQ(job->state(), QVirtualJob::Succeeded);
    EXPECT_TRUE(QFile::exists(directory.filePath("sftp-root/rename-slow-reply")));
}

TEST_F(QSshIntegration, VirtualSftpLostMutationReplyReportsUnknownOutcome) {
    write(directory.filePath("sftp-root/source"), "data");
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *session = ssh->createSftpSession();
    ASSERT_TRUE(ready(session));
    QSftpFileSystem fs(session);
    auto *job = fs.rename("/source", "/rename-drop-reply");
    ASSERT_TRUE(completed(job));
    EXPECT_EQ(job->error(), QVirtualJob::OutcomeUnknown);
    EXPECT_TRUE(QFile::exists(directory.filePath("sftp-root/rename-drop-reply")));
    EXPECT_FALSE(QFile::exists(directory.filePath("sftp-root/source")));
}
