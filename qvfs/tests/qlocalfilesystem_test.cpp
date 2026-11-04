#include <QCoreApplication>
#include <QDir>
#include <QElapsedTimer>
#include <QFile>
#include <QFileInfo>
#include <QPointer>
#include <QSet>
#include <QTemporaryDir>
#include <QThread>
#include <QThreadPool>
#include <gtest/gtest.h>
#include <qvfs/qlocalfilesystem.h>
#include <qvfs/qvirtualfile.h>

namespace {
    bool waitUntil(const std::function<bool()> &condition) {
        QElapsedTimer timer;
        timer.start();
        while (!condition() && timer.elapsed() < 5000) {
            QCoreApplication::processEvents(QEventLoop::AllEvents, 10);
            QThread::msleep(1);
        }
        return condition();
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

TEST(QLocalFileSystem, RootsUseQtDrivePathsAndDeliverAfterReturn) {
    QLocalFileSystem fs;
    auto *job = fs.roots();
    EXPECT_EQ(job->state(), QVirtualJob::Pending);
    QList<QVirtualFileInfo> roots;
    int finished = 0;
    QObject::connect(job, &QVirtualListJob::entriesReady, job, [&](const auto &entries) {
        EXPECT_EQ(QThread::currentThread(), fs.thread());
        roots.append(entries);
    });
    QObject::connect(job, &QVirtualJob::finished, job, [&] { ++finished; });
    ASSERT_TRUE(completed(job));
    EXPECT_EQ(job->state(), QVirtualJob::Succeeded);
    EXPECT_EQ(finished, 1);
    const auto drives = QDir::drives();
    ASSERT_EQ(roots.size(), drives.size());
    for (int i = 0; i < roots.size(); ++i) {
        auto *file = fs.file(drives[i].filePath());
        ASSERT_NE(file, nullptr);
        EXPECT_EQ(roots[i].path, file->path());
        EXPECT_TRUE(roots[i].isRoot);
        EXPECT_EQ(roots[i].type, QVirtualFileInfo::Directory);
        EXPECT_EQ(roots[i].name, roots[i].path);
        EXPECT_EQ(file->parentFile(), nullptr);
    }
    job->cancel();
    EXPECT_EQ(finished, 1);
}

TEST(QLocalFileSystem, FileReferencesAreLexicalAndCanRepresentMissingFiles) {
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    QLocalFileSystem fs;
    auto *file = fs.file(directory.path() + "/missing/../new");
    ASSERT_NE(file, nullptr);
    EXPECT_EQ(file->path(), directory.path() + "/new");
    EXPECT_EQ(file->name(), "new");
    EXPECT_EQ(file->parent(), &fs);
    EXPECT_EQ(file->parentFile()->path(), directory.path());
    EXPECT_EQ(file->child("nested")->path(), file->path() + "/nested");
    EXPECT_EQ(file->child("../escape"), nullptr);
    EXPECT_EQ(file->child(".."), nullptr);
    EXPECT_EQ(fs.file("relative/path"), nullptr);
    EXPECT_EQ(fs.file(QString()), nullptr);
    EXPECT_EQ(fs.file(directory.path() + QChar::Null + "bad"), nullptr);
    auto *job = file->stat();
    EXPECT_EQ(job->parent(), file);
    EXPECT_EQ(job->state(), QVirtualJob::Pending);
    ASSERT_TRUE(completed(job));
    EXPECT_EQ(job->error(), QVirtualJob::NotFound);
#ifdef Q_OS_WIN
    auto *drive = fs.file("c:\\a\\..\\");
    ASSERT_NE(drive, nullptr);
    EXPECT_EQ(drive->path(), "C:/");
    EXPECT_EQ(drive->parentFile(), nullptr);
    auto *share = fs.file("//host/share/../../");
    ASSERT_NE(share, nullptr);
    EXPECT_EQ(share->path(), "//host/share");
    EXPECT_EQ(share->parentFile(), nullptr);
    EXPECT_EQ(fs.file("C:relative"), nullptr);
    EXPECT_EQ(fs.file("/ambiguous"), nullptr);
#else
    EXPECT_EQ(file->child("back\\slash")->name(), "back\\slash");
#endif
}

TEST(QLocalFileSystem, StatReportsZeroSizeAndRejectsInvalidPathsAsynchronously) {
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    write(directory.filePath("empty"));
    write(directory.filePath("binary"), QByteArray("a\0b", 3));
    QLocalFileSystem fs;
    auto *empty = fs.stat(directory.filePath("empty"));
    auto *binary = fs.stat(directory.filePath("binary"));
    auto *invalid = fs.stat("relative");
    EXPECT_EQ(invalid->state(), QVirtualJob::Pending);
    ASSERT_TRUE(completed(empty));
    ASSERT_TRUE(completed(binary));
    ASSERT_TRUE(completed(invalid));
    EXPECT_EQ(empty->error(), QVirtualJob::NoError);
    EXPECT_EQ(empty->info().type, QVirtualFileInfo::File);
    EXPECT_TRUE(empty->info().attributes.testFlag(QVirtualFileInfo::Size));
    EXPECT_EQ(empty->info().size, 0u);
    EXPECT_EQ(binary->info().size, 3u);
    EXPECT_EQ(invalid->error(), QVirtualJob::InvalidPath);
}

TEST(QLocalFileSystem, ListsInBatchesWithoutDotEntriesAndSupportsHiddenFilter) {
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    for (int i = 0; i < 19; ++i) {
        write(directory.filePath(QString::number(i)), "data");
    }
    write(directory.filePath(".hidden"));
    ASSERT_TRUE(QDir(directory.path()).mkdir("subdir"));
    QLocalFileSystem fs;
    QVirtualListOptions options;
    options.batchSize = 3;
    auto *job = fs.list(directory.path(), options);
    EXPECT_EQ(job->state(), QVirtualJob::Pending);
    QSet<QString> names;
    int batches = 0;
    QObject::connect(job, &QVirtualListJob::entriesReady, job, [&](const auto &entries) {
        EXPECT_EQ(QThread::currentThread(), fs.thread());
        EXPECT_LE(entries.size(), 3);
        ++batches;
        for (const auto &info : entries) {
            EXPECT_EQ(info.path, directory.filePath(info.name));
            names.insert(info.name);
        }
    });
    ASSERT_TRUE(completed(job));
    EXPECT_EQ(job->error(), QVirtualJob::NoError);
    EXPECT_EQ(names.size(), 21);
    EXPECT_EQ(job->entriesCount(), 21u);
    EXPECT_EQ(batches, 7);
    EXPECT_FALSE(names.contains("."));
    EXPECT_FALSE(names.contains(".."));
    EXPECT_TRUE(names.contains(".hidden"));
    options.includeHidden = false;
    const bool dotFileIsHidden = QFileInfo(directory.filePath(".hidden")).isHidden();
    auto *visible = fs.list(directory.path(), options);
    QObject::connect(visible, &QVirtualListJob::entriesReady, visible, [dotFileIsHidden](const auto &entries) {
        if (dotFileIsHidden) {
            for (const auto &info : entries) {
                EXPECT_NE(info.name, ".hidden");
            }
        }
    });
    ASSERT_TRUE(completed(visible));
    EXPECT_EQ(visible->entriesCount(), dotFileIsHidden ? 20u : 21u);
}

TEST(QLocalFileSystem, InvalidDirectoriesAndOptionsFail) {
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    write(directory.filePath("file"));
    QLocalFileSystem fs;
    auto *missing = fs.list(directory.filePath("missing"));
    auto *file = fs.list(directory.filePath("file"));
    QVirtualListOptions options;
    options.batchSize = 0;
    auto *invalid = fs.list(directory.path(), options);
    ASSERT_TRUE(completed(missing));
    ASSERT_TRUE(completed(file));
    ASSERT_TRUE(completed(invalid));
    EXPECT_EQ(missing->error(), QVirtualJob::NotFound);
    EXPECT_EQ(file->error(), QVirtualJob::NotDirectory);
    EXPECT_EQ(invalid->error(), QVirtualJob::InvalidArgument);
}

TEST(QLocalFileSystem, CancelBeforeDeliveryFinishesOnceAndDoesNotDeliverEntries) {
    QLocalFileSystem fs;
    auto *job = fs.roots();
    int finished = 0;
    int entries = 0;
    QObject::connect(job, &QVirtualJob::finished, job, [&] { ++finished; });
    QObject::connect(job, &QVirtualListJob::entriesReady, job, [&](const auto &) { ++entries; });
    job->cancel();
    job->cancel();
    QCoreApplication::processEvents();
    EXPECT_EQ(job->state(), QVirtualJob::Cancelled);
    EXPECT_EQ(finished, 1);
    EXPECT_EQ(entries, 0);
}

TEST(QLocalFileSystem, CancelOrDeleteInsideBatchReleasesProducer) {
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    for (int i = 0; i < 40; ++i) {
        write(directory.filePath(QString::number(i)));
    }
    QLocalFileSystem fs;
    QVirtualListOptions options;
    options.batchSize = 1;
    auto *job = fs.list(directory.path(), options);
    int batches = 0;
    int finished = 0;
    QObject::connect(job, &QVirtualListJob::entriesReady, job, [&](const auto &) {
        ++batches;
        job->cancel();
    });
    QObject::connect(job, &QVirtualJob::finished, job, [&] { ++finished; });
    ASSERT_TRUE(completed(job));
    EXPECT_EQ(job->state(), QVirtualJob::Cancelled);
    EXPECT_EQ(batches, 1);
    EXPECT_EQ(finished, 1);
    QPointer<QVirtualListJob> deleted = fs.list(directory.path(), options);
    QObject::connect(deleted, &QVirtualListJob::entriesReady, deleted,
                     [&deleted](const auto &) { delete deleted.data(); });
    ASSERT_TRUE(waitUntil([&] { return deleted.isNull(); }));
    ASSERT_TRUE(QThreadPool::globalInstance()->waitForDone(5000));
}

TEST(QLocalFileSystem, ReferencesCanOutliveFileSystemWithExternalOwner) {
    QObject owner;
    auto *fs = new QLocalFileSystem;
    auto *file = fs->file(QDir::tempPath(), &owner);
    QPointer<QVirtualFile> owned = fs->file(QDir::tempPath());
    delete fs;
    EXPECT_TRUE(owned.isNull());
    EXPECT_EQ(file->fileSystem(), nullptr);
    EXPECT_EQ(file->parentFile(), nullptr);
    auto *stat = file->stat();
    auto *list = file->list();
    EXPECT_EQ(stat->state(), QVirtualJob::Pending);
    ASSERT_TRUE(completed(stat));
    ASSERT_TRUE(completed(list));
    EXPECT_EQ(stat->error(), QVirtualJob::FileSystemClosed);
    EXPECT_EQ(list->error(), QVirtualJob::FileSystemClosed);
}

TEST(QLocalFileSystem, DestroyFileSystemStopsOwnedJobs) {
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    for (int i = 0; i < 10; ++i) {
        write(directory.filePath(QString::number(i)));
    }
    auto *fs = new QLocalFileSystem;
    QPointer<QVirtualListJob> job = fs->list(directory.path(), {1, true});
    delete fs;
    EXPECT_TRUE(job.isNull());
    ASSERT_TRUE(QThreadPool::globalInstance()->waitForDone(5000));
}

#ifndef Q_OS_WIN
TEST(QLocalFileSystem, DanglingLinkIsReportedAsLinkWithoutFollowingTarget) {
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    const auto link = directory.filePath("link");
    ASSERT_TRUE(QFile::link(directory.filePath("missing"), link));
    QLocalFileSystem fs;
    auto *job = fs.stat(link);
    ASSERT_TRUE(completed(job));
    EXPECT_EQ(job->error(), QVirtualJob::NoError);
    EXPECT_EQ(job->info().type, QVirtualFileInfo::SymbolicLink);
    EXPECT_FALSE(job->info().attributes.testFlag(QVirtualFileInfo::Size));
}
#endif
