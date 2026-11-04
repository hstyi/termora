#include <QCoreApplication>
#include <QDir>
#include <QElapsedTimer>
#include <QFile>
#include <QPointer>
#include <QTemporaryDir>
#include <QThread>
#include <QThreadPool>
#include <condition_variable>
#include <gtest/gtest.h>
#include <qvfs/private/qvirtualbackend_p.h>
#include <qvfs/qlocalfilesystem.h>
#include <qvfs/qvirtualfile.h>

namespace {
    bool waitUntil(const std::function<bool()> &predicate) {
        QElapsedTimer timer;
        timer.start();
        while (!predicate() && timer.elapsed() < 5000) {
            QCoreApplication::processEvents(QEventLoop::AllEvents, 10);
            QThread::msleep(1);
        }
        return predicate();
    }

    bool done(QVirtualJob *job) {
        return waitUntil([&] { return job->isFinished(); });
    }

    void put(const QString &path, const QByteArray &data) {
        QFile file(path);
        ASSERT_TRUE(file.open(QIODevice::WriteOnly));
        ASSERT_EQ(file.write(data), data.size());
    }

    QByteArray get(const QString &path) {
        QFile file(path);
        return file.open(QIODevice::ReadOnly) ? file.readAll() : QByteArray();
    }

    QVirtualFileHandle *opened(QLocalFileSystem &fs, const QString &path, QVirtualOpenOptions options = {}) {
        auto *job = fs.open(path, options);
        EXPECT_EQ(job->state(), QVirtualJob::Pending);
        if (!done(job) || job->error() != QVirtualJob::NoError) {
            ADD_FAILURE() << job->errorString().toStdString();
            return nullptr;
        }
        auto *handle = job->takeHandle();
        EXPECT_EQ(job->takeHandle(), nullptr);
        delete job;
        return handle;
    }
} // namespace

TEST(QVirtualOperations, LocalReadWritePreservesContentsAndCloseIsBarrier) {
    QTemporaryDir dir;
    ASSERT_TRUE(dir.isValid());
    const auto path = dir.filePath("data");
    put(path, "abcdef");
    QLocalFileSystem fs;
    QVirtualOpenOptions options;
    options.access = QVirtualOpenOptions::ReadWrite;
    auto *handle = opened(fs, path, options);
    ASSERT_NE(handle, nullptr);
    EXPECT_EQ(handle->initialSize(), 6);
    auto *left = handle->writeAt(1, "12");
    auto *right = handle->writeAt(4, "XY");
    auto *read = handle->readAt(0);
    auto *close = handle->close();
    auto *late = handle->writeAt(0, "bad");
    ASSERT_TRUE(done(close));
    ASSERT_TRUE(done(read));
    ASSERT_TRUE(done(late));
    EXPECT_EQ(left->error(), QVirtualJob::NoError);
    EXPECT_EQ(right->bytesWritten(), 2);
    EXPECT_EQ(read->data(), "a12dXY");
    EXPECT_FALSE(read->atEnd());
    EXPECT_EQ(close->error(), QVirtualJob::NoError);
    EXPECT_EQ(late->error(), QVirtualJob::InvalidArgument);
    EXPECT_EQ(get(path), "a12dXY");
}

TEST(QVirtualOperations, OpenPoliciesAppendEofAndOwnership) {
    QTemporaryDir dir;
    ASSERT_TRUE(dir.isValid());
    QLocalFileSystem fs;
    const auto path = dir.filePath("data");
    put(path, "original");
    QVirtualOpenOptions options;
    options.access = QVirtualOpenOptions::WriteOnly;
    auto *write = opened(fs, path, options);
    ASSERT_NE(write, nullptr);
    auto *job = write->writeAt(0, "X");
    ASSERT_TRUE(done(job));
    ASSERT_TRUE(done(write->close()));
    EXPECT_EQ(get(path), "Xriginal");
    options.append = true;
    auto *append = opened(fs, path, options);
    ASSERT_NE(append, nullptr);
    append->writeAt(0, "1");
    append->writeAt(0, "2");
    ASSERT_TRUE(done(append->close()));
    EXPECT_EQ(get(path), "Xriginal12");
    options.append = false;
    options.creation = QVirtualOpenOptions::CreateNew;
    auto *exists = fs.open(path, options);
    ASSERT_TRUE(done(exists));
    EXPECT_EQ(exists->error(), QVirtualJob::AlreadyExists);
    auto *read = opened(fs, path);
    ASSERT_NE(read, nullptr);
    auto *eof = read->readAt(10);
    ASSERT_TRUE(done(eof));
    EXPECT_TRUE(eof->atEnd());
    EXPECT_TRUE(eof->data().isEmpty());
    auto *invalid = read->readAt(0, 0);
    ASSERT_TRUE(done(invalid));
    EXPECT_EQ(invalid->error(), QVirtualJob::InvalidArgument);
    ASSERT_TRUE(done(read->close()));
    auto *missing = fs.open(dir.filePath("missing"), {QVirtualOpenOptions::WriteOnly});
    ASSERT_TRUE(done(missing));
    EXPECT_EQ(missing->error(), QVirtualJob::NotFound);
    EXPECT_FALSE(QFile::exists(dir.filePath("missing")));
}

TEST(QVirtualOperations, AtomicReplacementCommitsOnlyOnExplicitSuccessfulClose) {
    QTemporaryDir dir;
    ASSERT_TRUE(dir.isValid());
    QLocalFileSystem fs;
    const auto path = dir.filePath("document");
    put(path, "old");
    QVirtualOpenOptions options;
    options.access = QVirtualOpenOptions::WriteOnly;
    options.creation = QVirtualOpenOptions::Replace;
    options.atomicReplace = true;
    auto *handle = opened(fs, path, options);
    ASSERT_NE(handle, nullptr);
    ASSERT_TRUE(done(handle->writeAt(0, "new")));
    EXPECT_EQ(get(path), "old");
    delete handle;
    ASSERT_TRUE(QThreadPool::globalInstance()->waitForDone(5000));
    EXPECT_EQ(get(path), "old");
    handle = opened(fs, path, options);
    ASSERT_NE(handle, nullptr);
    ASSERT_TRUE(done(handle->writeAt(0, "complete")));
    ASSERT_TRUE(done(handle->close()));
    EXPECT_EQ(get(path), "complete");
}

TEST(QVirtualOperations, DeleteRenameCancellationAndRootProtection) {
    QTemporaryDir dir;
    ASSERT_TRUE(dir.isValid());
    QLocalFileSystem fs;
    const auto source = dir.filePath("source"), target = dir.filePath("target");
    put(source, "source");
    put(target, "target");
    auto *src = fs.file(source);
    auto *dst = fs.file(target);
    auto *conflict = src->renameTo(dst);
    ASSERT_TRUE(done(conflict));
    EXPECT_EQ(conflict->error(), QVirtualJob::AlreadyExists);
    EXPECT_EQ(get(source), "source");
    EXPECT_EQ(get(target), "target");
    QVirtualRenameOptions options;
    options.overwrite = true;
    auto *renamed = src->renameTo(dst, options);
    ASSERT_TRUE(done(renamed));
    EXPECT_EQ(renamed->error(), QVirtualJob::NoError);
    EXPECT_EQ(renamed->affectedCount(), 1u);
    EXPECT_EQ(src->path(), source);
    EXPECT_EQ(get(target), "source");
    auto *cancelled = dst->remove();
    cancelled->cancel();
    EXPECT_TRUE(cancelled->isCancellationRequested());
    ASSERT_TRUE(done(cancelled));
    EXPECT_EQ(cancelled->state(), QVirtualJob::Cancelled);
    EXPECT_TRUE(QFile::exists(target));
    ASSERT_TRUE(QDir(dir.path()).mkdir("folder"));
    put(dir.filePath("folder/child"), "data");
    auto *nonempty = fs.remove(dir.filePath("folder"));
    ASSERT_TRUE(done(nonempty));
    EXPECT_EQ(nonempty->error(), QVirtualJob::NotEmpty);
    auto *recursive = fs.remove(dir.filePath("folder"), {true, false});
    ASSERT_TRUE(done(recursive));
    EXPECT_EQ(recursive->error(), QVirtualJob::NoError);
    EXPECT_EQ(recursive->affectedCount(), 2u);
    auto *missing = fs.remove(dir.filePath("folder"), {false, true});
    ASSERT_TRUE(done(missing));
    EXPECT_EQ(missing->error(), QVirtualJob::NoError);
    auto *root = fs.remove(QDir::rootPath(), {true, false});
    ASSERT_TRUE(done(root));
    EXPECT_NE(root->error(), QVirtualJob::NoError);
}

TEST(QVirtualOperations, RecursiveDeletionDoesNotFollowLinks) {
#ifdef Q_OS_WIN
    GTEST_SKIP() << "Unix symlink semantics";
#else
    QTemporaryDir dir;
    ASSERT_TRUE(dir.isValid());
    QLocalFileSystem fs;
    ASSERT_TRUE(QDir(dir.path()).mkdir("keep"));
    put(dir.filePath("keep/file"), "keep");
    ASSERT_TRUE(QDir(dir.path()).mkdir("delete"));
    ASSERT_TRUE(QFile::link(dir.filePath("keep"), dir.filePath("delete/link")));
    auto *job = fs.remove(dir.filePath("delete"), {true, false});
    ASSERT_TRUE(done(job));
    EXPECT_EQ(job->error(), QVirtualJob::NoError);
    EXPECT_EQ(job->affectedCount(), 2u);
    EXPECT_EQ(get(dir.filePath("keep/file")), "keep");
#endif
}

TEST(QVirtualOperations, LocalCopiesMoveAndOverwritePolicies) {
    QTemporaryDir dir;
    ASSERT_TRUE(dir.isValid());
    QLocalFileSystem fs;
    QByteArray data(9 * 65536 + 13, Qt::Uninitialized);
    for (int i = 0; i < data.size(); ++i) {
        data[i] = static_cast<char>(i * 31);
    }
    auto *source = fs.file(dir.filePath("source"));
    auto *target = fs.file(dir.filePath("target"));
    put(source->path(), data);
    QVirtualTransferOptions options;
    options.bufferSize = 2 * 65536;
    auto *copy = source->copyTo(target, options);
    EXPECT_EQ(copy->state(), QVirtualJob::Pending);
    ASSERT_TRUE(done(copy));
    EXPECT_EQ(copy->error(), QVirtualJob::NoError) << copy->errorString().toStdString();
    EXPECT_EQ(copy->bytesTransferred(), data.size());
    EXPECT_EQ(get(target->path()), data);
    auto *conflict = source->copyTo(target);
    ASSERT_TRUE(done(conflict));
    EXPECT_EQ(conflict->error(), QVirtualJob::AlreadyExists);
    auto *self = source->copyTo(source, {true});
    ASSERT_TRUE(done(self));
    EXPECT_EQ(self->error(), QVirtualJob::InvalidArgument);
    options.overwrite = true;
    options.atomicReplace = true;
    auto *overwrite = source->copyTo(target, options);
    ASSERT_TRUE(done(overwrite));
    EXPECT_EQ(overwrite->error(), QVirtualJob::NoError);
    auto *moved = source->moveTo(fs.file(dir.filePath("moved")));
    ASSERT_TRUE(done(moved));
    EXPECT_EQ(moved->error(), QVirtualJob::NoError);
    EXPECT_FALSE(QFile::exists(source->path()));
    EXPECT_EQ(get(dir.filePath("moved")), data);
    put(source->path(), {});
    auto *empty = source->copyTo(fs.file(dir.filePath("empty")));
    ASSERT_TRUE(done(empty));
    EXPECT_EQ(empty->error(), QVirtualJob::NoError);
    EXPECT_EQ(empty->bytesTransferred(), 0);
    EXPECT_TRUE(QFile::exists(dir.filePath("empty")));
}

namespace {
    /** 模拟不依赖 Qt 事件循环的异步后端，短读和逆序响应验证通用流水线契约。 */
    struct MemoryState {
        QByteArray bytes;
        bool unknownSize = false, reverse = false, sequential = false, denyRemove = false;
        std::mutex mutex;
        std::condition_variable closed;
        bool committed = false;
        std::atomic_int maxConcurrentReads = 0;
    };

    class MemoryHandle final : public qvfs::Handle {
    public:
        MemoryHandle(std::shared_ptr<MemoryState> state, bool write) : state(std::move(state)), writable(write) {}

        bool randomWrite() const override {
            return !state->sequential;
        }

        void read(quint64 offset, quint32 count, qvfs::ControlPtr control, qvfs::Callback cb) override {
            qvfs::Result r;
            if (control->cancelled) {
                r = qvfs::failure(QVirtualJob::OperationCancelled, "cancelled");
            } else {
                const auto n = qMin<quint64>(qMin(count, 8192u), offset < quint64(state->bytes.size())
                                                                     ? quint64(state->bytes.size()) - offset
                                                                     : 0);
                r.data = state->bytes.mid(static_cast<int>(qMin<quint64>(offset, state->bytes.size())),
                                          static_cast<int>(n));
                r.eof = n == 0;
            }
            if (!state->reverse) {
                cb(std::move(r));
                return;
            }
            responses.push_back([cb = std::move(cb), r] { cb(r); });
            state->maxConcurrentReads = qMax(state->maxConcurrentReads.load(), static_cast<int>(responses.size()));
            if (responses.size() == 2) {
                auto replies = std::move(responses);
                responses.clear();
                for (auto it = replies.rbegin(); it != replies.rend(); ++it) {
                    (*it)();
                }
            }
        }

        void write(quint64 offset, QByteArray data, qvfs::ControlPtr control, qvfs::Callback cb) override {
            qvfs::Result r;
            if (control->cancelled) {
                r = qvfs::failure(QVirtualJob::OperationCancelled, "cancelled");
            } else {
                if (state->sequential && offset != quint64(state->bytes.size())) {
                    cb(qvfs::failure(QVirtualJob::InvalidArgument, "sequential write order"));
                    return;
                }
                if (offset + data.size() > quint64(state->bytes.size())) {
                    state->bytes.resize(static_cast<int>(offset + data.size()));
                }
                std::copy(data.begin(), data.end(), state->bytes.begin() + static_cast<int>(offset));
                r.affected = data.size();
            }
            cb(std::move(r));
        }

        void close(bool commit, qvfs::Callback cb) override {
            if (writable && commit) {
                {
                    std::lock_guard lock(state->mutex);
                    state->committed = true;
                }
                state->closed.notify_all();
            }
            cb({});
        }

    private:
        std::shared_ptr<MemoryState> state;
        bool writable;
        std::vector<std::function<void()>> responses;
    };

    class MemoryBackend final : public qvfs::Backend {
    public:
        explicit MemoryBackend(std::shared_ptr<MemoryState> state) : state(std::move(state)) {}

        const void *identity() const override {
            return state.get();
        }

        QString path() const override {
            return "/file";
        }

        void open(QVirtualOpenOptions o, qvfs::ControlPtr control, qvfs::Callback cb) override {
            if (control->cancelled) {
                cb(qvfs::failure(QVirtualJob::OperationCancelled, "cancelled"));
                return;
            }
            const bool write = o.access != QVirtualOpenOptions::ReadOnly;
            if (write) {
                state->bytes.clear();
            }
            qvfs::Result r;
            r.size = state->unknownSize ? -1 : state->bytes.size();
            r.handle = std::make_shared<MemoryHandle>(state, write);
            cb(std::move(r));
        }

        void remove(QVirtualRemoveOptions, qvfs::ControlPtr, qvfs::Callback cb) override {
            cb(state->denyRemove ? qvfs::failure(QVirtualJob::PermissionDenied, "source retained") : qvfs::Result());
        }

        void rename(QString, QVirtualRenameOptions, qvfs::ControlPtr, qvfs::Callback cb) override {
            cb(qvfs::failure(QVirtualJob::Unsupported, "unsupported"));
        }

    private:
        std::shared_ptr<MemoryState> state;
    };

    class MemoryFs final : public QVirtualFileSystem {
    public:
        std::shared_ptr<MemoryState> memory = std::make_shared<MemoryState>();

        QVirtualListJob *roots(QObject *) override {
            return nullptr;
        }

        QVirtualStatJob *stat(const QString &, QObject *) override {
            return nullptr;
        }

        QVirtualListJob *list(const QString &, const QVirtualListOptions &, QObject *) override {
            return nullptr;
        }

    protected:
        QString normalizePath(const QString &p) const override {
            return p;
        }

        QString parentPath(const QString &) const override {
            return {};
        }

        QString childPath(const QString &, const QString &) const override {
            return {};
        }

        QString fileName(const QString &) const override {
            return "file";
        }

        std::shared_ptr<qvfs::Backend> backend(const QString &) const override {
            return std::make_shared<MemoryBackend>(memory);
        }
    };
} // namespace

TEST(QVirtualOperations, GenericPipelineHandlesShortReadsReorderingAndSequentialDestination) {
    MemoryFs source, target;
    source.memory->bytes = QByteArray(2 * 65536, 'x');
    source.memory->reverse = true;
    target.memory->sequential = true;
    auto *job = source.file("/file")->copyTo(target.file("/file"), {false, false, 2 * 65536});
    ASSERT_TRUE(done(job));
    EXPECT_EQ(job->error(), QVirtualJob::NoError) << job->errorString().toStdString();
    EXPECT_EQ(source.memory->maxConcurrentReads, 2);
    EXPECT_EQ(target.memory->bytes, source.memory->bytes);
}

TEST(QVirtualOperations, GenericTransferAdvancesWhileGuiDoesNotProcessEvents) {
    MemoryFs source, target;
    source.memory->bytes = QByteArray(5 * 65536 + 7, 'y');
    source.memory->unknownSize = true;
    auto *job = source.file("/file")->copyTo(target.file("/file"));
    QCoreApplication::processEvents(); // 只启动一次，后续不通过 GUI 消费块数据。
    {
        std::unique_lock lock(target.memory->mutex);
        ASSERT_TRUE(
            target.memory->closed.wait_for(lock, std::chrono::seconds(3), [&] { return target.memory->committed; }));
    }
    ASSERT_TRUE(done(job));
    EXPECT_EQ(job->error(), QVirtualJob::NoError);
    EXPECT_EQ(target.memory->bytes, source.memory->bytes);
    EXPECT_EQ(job->totalBytes(), source.memory->bytes.size());
}

TEST(QVirtualOperations, BudgetsCancellationAndPartialMoveAreReported) {
    MemoryFs source, target;
    source.memory->bytes = QByteArray(65536, 'z');
    const auto previous = QVirtualTransferJob::globalBufferLimit();
    ASSERT_TRUE(QVirtualTransferJob::setGlobalBufferLimit(65536));
    auto *first = source.file("/file")->copyTo(target.file("/file"), {false, false, 65536});
    auto *second = source.file("/file")->copyTo(target.file("/file"), {false, false, 65536});
    first->cancel();
    ASSERT_TRUE(QVirtualTransferJob::setGlobalBufferLimit(previous));
    ASSERT_TRUE(done(first));
    ASSERT_TRUE(done(second));
    EXPECT_EQ(first->state(), QVirtualJob::Cancelled);
    EXPECT_EQ(second->error(), QVirtualJob::ResourceLimit);
    EXPECT_TRUE(target.memory->bytes.isEmpty());
    source.memory->denyRemove = true;
    auto *move = source.file("/file")->moveTo(target.file("/file"));
    ASSERT_TRUE(done(move));
    EXPECT_EQ(move->error(), QVirtualJob::PartialFailure);
    EXPECT_EQ(target.memory->bytes, source.memory->bytes);
}
