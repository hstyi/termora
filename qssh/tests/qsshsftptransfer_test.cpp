#include "qssh_test_support.h"

#include <QBuffer>
#include <QFile>
#include <QPointer>
#include <QThread>
#include <QTimer>
#include <atomic>
#include <qssh/qsshclient.h>
#include <qssh/qsshexecchannel.h>
#include <qssh/qsshsftpsession.h>

using qssh_test::waitUntil;
using Transfer = QSshSftpTransfer;

namespace {
    /** 等待终态，不把进度达到 100% 当作传输成功。 */
    bool done(Transfer *transfer) {
        return waitUntil([&] { return transfer->state() >= Transfer::Succeeded; });
    }

    /** 生成包含 NUL 和非 UTF-8 字节的内容，避免只验证文本通路。 */
    QByteArray binary(int size) {
        QByteArray bytes(size, Qt::Uninitialized);
        for (int i = 0; i < size; ++i) {
            bytes[i] = static_cast<char>((i * 41) & 255);
        }
        return bytes;
    }

    /** 记录公开进度，校验线程、单调性和终结通知次数。 */
    struct Observation {
        explicit Observation(Transfer *transfer) {
            QObject::connect(transfer, &Transfer::progress, transfer, [this, transfer](qint64 n, qint64 total) {
                EXPECT_EQ(QThread::currentThread(), transfer->thread());
                EXPECT_GE(n, previous);
                if (total >= 0) {
                    EXPECT_LE(n, total);
                }
                previous = n;
                totals.append(total);
                ++updates;
            });
            QObject::connect(transfer, &Transfer::finished, transfer, [this] { ++finishes; });
        }

        qint64 previous = 0;           ///< 最近已交付进度。
        QList<qint64> totals;          ///< 包括未知总量和最终已知总量。
        int updates = 0, finishes = 0; ///< 信号计数。
    };

    /** 一个受控顺序输入设备，暂时空读时还未 EOF，由 finishInput 显式结束。 */
    class SequentialSource : public QIODevice {
    public:
        SequentialSource() {
            open(ReadOnly);
        }

        bool isSequential() const override {
            return true;
        }

        /** 在 Qt 线程追加下一批 bytes 并通知桥接器。 */
        void append(const QByteArray &bytes) {
            content.append(bytes);
            Q_EMIT readyRead();
        }

    protected:
        qint64 readData(char *target, qint64 maximum) override {
            EXPECT_EQ(QThread::currentThread(), thread());
            const auto count = std::min<qint64>(maximum, content.size());
            memcpy(target, content.constData(), count);
            content.remove(0, static_cast<int>(count));
            return count;
        }

        qint64 writeData(const char *, qint64) override {
            return -1;
        }

    private:
        QByteArray content; ///< 尚未消费的源内容。
    };

    /** 目标首次返回零并异步通知可写，后续只接受短写，验证设备背压。 */
    class ShortSink : public QIODevice {
    public:
        ShortSink() {
            open(WriteOnly);
        }

        QByteArray content;     ///< 已实际接受的内容。
        bool autoResume = true; ///< 可由测试关闭自动恢复，确定性验证背压期间的进度。
        bool attempted = false; ///< 已实际尝试写入。

        /** 解除背压并通知设备桥接器。 */
        void resume() {
            writable = true;
            Q_EMIT bytesWritten(0);
        }

    protected:
        qint64 readData(char *, qint64) override {
            return -1;
        }

        qint64 writeData(const char *data, qint64 size) override {
            EXPECT_EQ(QThread::currentThread(), thread());
            if (!writable) {
                attempted = true;
                if (autoResume) {
                    QTimer::singleShot(5, this, [this] { resume(); });
                }
                return 0;
            }
            const auto count = std::min<qint64>(size, 997);
            content.append(data, static_cast<int>(count));
            return count;
        }

    private:
        bool writable = false; ///< 首次写入模拟设备不可写。
    };

    /** 模拟由应用另一个 Qt 线程拥有的慢源，检查桥接器不会跨线程直接调用设备。 */
    class ForeignBuffer : public QBuffer {
    public:
        std::atomic_bool reading = false; ///< 供测试等待实际进入 readData。
        bool slow = false;                ///< 在设备线程模拟慢本地 I/O。
    protected:
        qint64 readData(char *data, qint64 size) override {
            EXPECT_EQ(QThread::currentThread(), thread());
            reading = true;
            if (slow) {
                QThread::msleep(30);
            }
            return QBuffer::readData(data, size);
        }
    };

    /** 即使测试断言失败也回收应用设备线程；此线程不是 qssh 创建的 worker。 */
    class DeviceThread : public QThread {
    public:
        ~DeviceThread() override {
            quit();
            wait();
        }
    };

} // namespace

/** 路径上传和下载完整往返，保持单 worker，进度仅在写入已确认／落入目标后累计。 */
TEST_F(QSshIntegration, SftpTransferPathsProgressAndOverwrite) {
    const auto bytes = binary(1024 * 1024 + 31);
    const auto source = directory.path() + "/source";
    QFile input(source);
    ASSERT_TRUE(input.open(QIODevice::WriteOnly));
    ASSERT_EQ(input.write(bytes), bytes.size());
    input.close();
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    auto *upload = sftp->upload(source, "/remote");
    Observation uploaded(upload);
    ASSERT_TRUE(done(upload));
    ASSERT_EQ(upload->state(), Transfer::Succeeded) << upload->errorString().toStdString();
    EXPECT_EQ(upload->bytesTransferred(), bytes.size());
    EXPECT_EQ(upload->totalBytes(), bytes.size());
    EXPECT_EQ(uploaded.finishes, 1);
    const auto target = directory.path() + "/target";
    auto *download = sftp->download("/remote", target);
    Observation downloaded(download);
    ASSERT_TRUE(done(download));
    ASSERT_EQ(download->state(), Transfer::Succeeded) << download->errorString().toStdString();
    EXPECT_EQ(downloaded.previous, bytes.size());
    EXPECT_EQ(downloaded.finishes, 1);
    QFile result(target);
    ASSERT_TRUE(result.open(QIODevice::ReadOnly));
    EXPECT_EQ(result.readAll(), bytes);
    result.close();
    auto *refused = sftp->download("/remote", target);
    ASSERT_TRUE(done(refused));
    EXPECT_EQ(refused->state(), Transfer::Failed);
    auto *remoteRefused = sftp->upload(source, "/remote");
    ASSERT_TRUE(done(remoteRefused));
    EXPECT_EQ(remoteRefused->state(), Transfer::Failed);
    Transfer::Options overwrite;
    overwrite.overwrite = true;
    auto *replaced = sftp->download("/remote", target, overwrite);
    ASSERT_TRUE(done(replaced));
    EXPECT_EQ(replaced->state(), Transfer::Succeeded);
    auto *remoteReplaced = sftp->upload(source, "/remote", overwrite);
    ASSERT_TRUE(done(remoteReplaced));
    EXPECT_EQ(remoteReplaced->state(), Transfer::Succeeded);
}

/** QBuffer 从当前位置传输，不取得所有权；两个 READ 在回复前发出且逆序返回。 */
TEST_F(QSshIntegration, SftpTransferDevicesPipeliningAndOrderedOutput) {
    const auto bytes = binary(2 * 65536);
    QBuffer source;
    source.setData("prefix" + bytes);
    ASSERT_TRUE(source.open(QIODevice::ReadOnly));
    ASSERT_TRUE(source.seek(6));
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    auto *upload = sftp->upload(&source, "/pipeline-transfer");
    Observation sent(upload);
    ASSERT_TRUE(done(upload));
    ASSERT_EQ(upload->state(), Transfer::Succeeded) << upload->errorString().toStdString();
    EXPECT_TRUE(source.isOpen());
    EXPECT_EQ(sent.previous, bytes.size());
    QBuffer destination;
    ASSERT_TRUE(destination.open(QIODevice::ReadWrite));
    destination.write("prefix");
    auto *download = sftp->download("/pipeline-transfer", &destination);
    Observation received(download);
    ASSERT_TRUE(done(download));
    ASSERT_EQ(download->state(), Transfer::Succeeded) << download->errorString().toStdString();
    EXPECT_EQ(destination.data(), "prefix" + bytes);
    EXPECT_TRUE(destination.isOpen());
    EXPECT_EQ(received.finishes, 1);
}

/** 顺序源可暂时无数据，显式 EOF 后才结束；顺序目标正确处理零写入和短写。 */
TEST_F(QSshIntegration, SftpTransferSequentialInputAndSinkBackpressure) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    SequentialSource source;
    auto *upload = sftp->upload(&source, "/stream");
    Observation observed(upload);
    ASSERT_TRUE(waitUntil([&] { return upload->state() == Transfer::Running; }));
    EXPECT_EQ(upload->totalBytes(), -1);
    const auto bytes = binary(200003);
    source.append(bytes.left(90000));
    ASSERT_TRUE(waitUntil([&] { return upload->bytesTransferred() >= 90000; }));
    EXPECT_EQ(upload->state(), Transfer::Running);
    auto *exec = ssh->createExecChannel();
    exec->open("during-transfer");
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
    EXPECT_EQ(exec->readAll(), "during-transfer");
    QBuffer parallel;
    parallel.setData("parallel");
    parallel.open(QIODevice::ReadOnly);
    auto *other = sftp->upload(&parallel, "/parallel");
    ASSERT_TRUE(done(other));
    EXPECT_EQ(other->state(), Transfer::Succeeded);
    EXPECT_EQ(upload->state(), Transfer::Running);
    source.append(bytes.mid(90000));
    upload->finishInput();
    ASSERT_TRUE(done(upload));
    ASSERT_EQ(upload->state(), Transfer::Succeeded) << upload->errorString().toStdString();
    EXPECT_EQ(upload->totalBytes(), bytes.size());
    EXPECT_TRUE(observed.totals.contains(-1));
    ShortSink sink;
    auto *download = sftp->download("/stream", &sink);
    ASSERT_TRUE(done(download));
    ASSERT_EQ(download->state(), Transfer::Succeeded) << download->errorString().toStdString();
    EXPECT_EQ(sink.content, bytes);
}

/** 空文件、短读、缺失路径、只读目标和部分文件清理均有明确终态。 */
TEST_F(QSshIntegration, SftpTransferEmptyShortReadsAndFailures) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    QBuffer empty;
    empty.open(QIODevice::ReadOnly);
    auto *upload = sftp->upload(&empty, "/empty");
    ASSERT_TRUE(done(upload));
    ASSERT_EQ(upload->state(), Transfer::Succeeded);
    QBuffer target;
    target.open(QIODevice::ReadWrite);
    auto *download = sftp->download("/empty", &target);
    Observation zero(download);
    ASSERT_TRUE(done(download));
    EXPECT_EQ(download->state(), Transfer::Succeeded);
    EXPECT_EQ(download->totalBytes(), 0);
    EXPECT_EQ(zero.finishes, 1);
    const auto bytes = binary(300001);
    QBuffer content;
    content.setData(bytes);
    content.open(QIODevice::ReadOnly);
    auto *shortSource = sftp->upload(&content, "/short-read");
    ASSERT_TRUE(done(shortSource));
    ASSERT_EQ(shortSource->state(), Transfer::Succeeded);
    ShortSink shortTarget;
    auto *shortRead = sftp->download("/short-read", &shortTarget);
    ASSERT_TRUE(done(shortRead));
    ASSERT_EQ(shortRead->state(), Transfer::Succeeded) << shortRead->errorString().toStdString();
    EXPECT_EQ(shortTarget.content, bytes);
    const auto missingTarget = directory.path() + "/missing-target";
    auto *missing = sftp->download("/missing", missingTarget);
    ASSERT_TRUE(done(missing));
    EXPECT_EQ(missing->state(), Transfer::Failed);
    EXPECT_FALSE(QFile::exists(missingTarget));
    QBuffer readonly;
    readonly.open(QIODevice::ReadOnly);
    auto *invalid = sftp->download("/empty", &readonly);
    ASSERT_TRUE(done(invalid));
    EXPECT_EQ(invalid->error(), Transfer::LocalIoError);
    auto *nullDevice = sftp->upload(static_cast<QIODevice *>(nullptr), "/null");
    ASSERT_TRUE(done(nullDevice));
    EXPECT_EQ(nullDevice->error(), Transfer::InvalidConfiguration);
}

/** 取消或销毁挂起的流操作只影响当前传输，同连接 exec 及后续传输仍正常。 */
TEST_F(QSshIntegration, SftpTransferCancelDestructionAndSessionClose) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    SequentialSource source;
    Transfer::Options options;
    options.removePartialOnFailure = true;
    auto *cancelled = sftp->upload(&source, "/cancelled", options);
    Observation observation(cancelled);
    ASSERT_TRUE(waitUntil([&] { return cancelled->state() == Transfer::Running; }));
    cancelled->cancel();
    ASSERT_TRUE(done(cancelled));
    EXPECT_EQ(cancelled->state(), Transfer::Cancelled);
    EXPECT_EQ(observation.finishes, 1);
    auto *exec = ssh->createExecChannel();
    exec->open("after-cancel");
    ASSERT_TRUE(waitUntil([&] { return exec->state() == QSshChannel::Closed; }));
    EXPECT_EQ(exec->readAll(), "after-cancel");
    ASSERT_TRUE(waitUntil([&] { return !QFile::exists(directory.path() + "/sftp-root/cancelled"); }));
    for (int i = 0; i < 5; ++i) {
        auto *pending = sftp->upload(&source, QString("/deleted-%1").arg(i));
        delete pending;
    }
    auto *waiting = sftp->upload(&source, "/waiting");
    ASSERT_TRUE(waitUntil([&] { return waiting->state() == Transfer::Running; }));
    ssh->close();
    ASSERT_TRUE(done(waiting));
    EXPECT_EQ(waiting->error(), Transfer::SessionClosed);
}

/** 外部设备无数据时的空闲超时、源被销毁和进度信号内删除均安全结束。 */
TEST_F(QSshIntegration, SftpTransferTimeoutDeviceDeletionAndSignalDeletion) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    SequentialSource source;
    Transfer::Options options;
    options.idleTimeoutMs = 200;
    auto *timed = sftp->upload(&source, "/timeout", options);
    ASSERT_TRUE(done(timed));
    EXPECT_EQ(timed->error(), Transfer::TimeoutError);
    auto *borrowed = new SequentialSource;
    auto *deleted = sftp->upload(borrowed, "/device-deleted");
    ASSERT_TRUE(waitUntil([&] { return deleted->state() == Transfer::Running; }));
    delete borrowed;
    ASSERT_TRUE(done(deleted));
    EXPECT_EQ(deleted->error(), Transfer::LocalIoError);
    QBuffer input;
    input.setData(binary(1024 * 1024));
    input.open(QIODevice::ReadOnly);
    QPointer<Transfer> fromSignal = sftp->upload(&input, "/signal-deleted");
    QObject::connect(fromSignal, &Transfer::progress, fromSignal, [job = fromSignal.data()](qint64 bytes, qint64) {
        if (bytes) {
            delete job;
        }
    });
    ASSERT_TRUE(waitUntil([&] { return fromSignal.isNull(); }));
    delete sftp;
}

/** 已有应用线程上的设备保持原归属，传输和 Client 析构不会留下迟到的 worker 投递。 */
TEST_F(QSshIntegration, SftpTransferForeignDeviceThreadAndClientDestruction) {
    DeviceThread deviceThread;
    auto *source = new ForeignBuffer;
    source->setData(binary(256 * 1024));
    source->open(QIODevice::ReadOnly);
    source->moveToThread(&deviceThread);
    QObject::connect(&deviceThread, &QThread::finished, source, &QObject::deleteLater);
    deviceThread.start();
    auto client = std::make_unique<QSshClient>();
    auto *ssh = client->createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    auto *upload = sftp->upload(source, "/foreign");
    ASSERT_TRUE(done(upload));
    ASSERT_EQ(upload->state(), Transfer::Succeeded) << upload->errorString().toStdString();
    QMetaObject::invokeMethod(
        source,
        [source] {
            source->seek(0);
            source->reading = false;
            source->slow = true;
        },
        Qt::BlockingQueuedConnection);
    sftp->upload(source, "/destroy-client");
    ASSERT_TRUE(waitUntil([&] { return source->reading.load(); }));
    client.reset(); // 设备 readData 尚未返回，原 worker 必须仍然安全退出。
    deviceThread.quit();
    deviceThread.wait();
}

/** 服务器不提供 SIZE 时由实际 EOF 确定总量，乱序到达的越界 EOF 不留下空洞。 */
TEST_F(QSshIntegration, SftpTransferUnknownDownloadSize) {
    const auto bytes = binary(200003);
    QFile fixture(directory.path() + "/sftp-root/unknown-size");
    ASSERT_TRUE(fixture.open(QIODevice::WriteOnly));
    fixture.write(bytes);
    fixture.close();
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    QBuffer target;
    target.open(QIODevice::ReadWrite);
    auto *download = sftp->download("/unknown-size", &target);
    Observation observed(download);
    ASSERT_TRUE(done(download));
    ASSERT_EQ(download->state(), Transfer::Succeeded) << download->errorString().toStdString();
    EXPECT_EQ(target.data(), bytes);
    EXPECT_EQ(download->totalBytes(), bytes.size());
    EXPECT_TRUE(observed.totals.contains(-1));
}

/** OPEN 已发出但成功响应尚未回来时取消并删除传输，迟到句柄及部分远端文件仍回收。 */
TEST_F(QSshIntegration, SftpTransferCancelledLateOpenIsCleanedUp) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    QBuffer source;
    source.setData("not-sent");
    source.open(QIODevice::ReadOnly);
    Transfer::Options options;
    options.removePartialOnFailure = true;
    auto *upload = sftp->upload(&source, "/late-open", options);
    QByteArray marker;
    ASSERT_TRUE(waitUntil([&] {
        marker += server.readAllStandardOutput();
        return marker.contains("SFTP_OPEN_STARTED");
    }));
    upload->cancel();
    ASSERT_TRUE(done(upload));
    EXPECT_EQ(upload->state(), Transfer::Cancelled);
    delete upload;
    bool barrier = false;
    QObject::connect(sftp, &QSshSftpSession::requestFinished, sftp, [&](quint64) { barrier = true; });
    ASSERT_NE(sftp->stat("/"), 0u);
    ASSERT_TRUE(waitUntil([&] { return barrier; }));
    ASSERT_TRUE(waitUntil([&] { return !QFile::exists(directory.path() + "/sftp-root/late-open"); }));
    EXPECT_EQ(sftp->state(), QSshSftpSession::Ready);
}

/** 上传尚未收到写确认、下载目标尚未接受数据时，进度都不能提前增加。 */
TEST_F(QSshIntegration, SftpTransferProgressWaitsForAcknowledgementAndDestination) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    SequentialSource source;
    source.append(QByteArray(65536, 'a'));
    auto *upload = sftp->upload(&source, "/pipeline-progress");
    Observation sent(upload);
    QByteArray marker;
    ASSERT_TRUE(waitUntil([&] {
        marker += server.readAllStandardOutput();
        return marker.contains("SFTP_FIRST_DATA_REQUEST");
    }));
    EXPECT_EQ(upload->bytesTransferred(), 0);
    EXPECT_EQ(sent.previous, 0);
    source.append(QByteArray(65536, 'b'));
    upload->finishInput();
    ASSERT_TRUE(done(upload));
    ASSERT_EQ(upload->state(), Transfer::Succeeded);
    EXPECT_EQ(sent.previous, 131072);
    ShortSink sink;
    sink.autoResume = false;
    auto *download = sftp->download("/pipeline-progress", &sink);
    Observation received(download);
    ASSERT_TRUE(waitUntil([&] { return sink.attempted; }));
    EXPECT_EQ(download->bytesTransferred(), 0);
    EXPECT_EQ(received.previous, 0);
    sink.resume();
    ASSERT_TRUE(done(download));
    ASSERT_EQ(download->state(), Transfer::Succeeded);
    EXPECT_EQ(sink.content, QByteArray(65536, 'a') + QByteArray(65536, 'b'));
    EXPECT_EQ(received.previous, 131072);
}
