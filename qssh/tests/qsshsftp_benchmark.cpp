#include "qssh_test_support.h"

#include <QBuffer>
#include <QCryptographicHash>
#include <QDateTime>
#include <QElapsedTimer>
#include <QEventLoop>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcessEnvironment>
#include <QSaveFile>
#include <QSysInfo>
#include <QThread>
#include <QTimer>
#include <algorithm>
#include <iomanip>
#include <iostream>
#include <libssh/libssh.h>
#include <qssh/qsshclient.h>
#include <qssh/qsshsftpsession.h>

namespace {
    constexpr qint64 MiB = 1024 * 1024; ///< 所有速率统一使用二进制 MiB/s。

    /** 一轮完整传输的结果，计时包括准备文件、传输、CLOSE 和本地提交，不包含连接认证及校验。 */
    struct Sample {
        double seconds = 0; ///< 从调用 upload/download 到 finished 的耗时。
        QString error;      ///< 空字符串表示成功。
    };

    /** 在真实 Qt 事件循环中计时；不使用功能测试中的 1 ms sleep，以免人为限制设备吞吐。 */
    Sample measure(const std::function<QSshSftpTransfer *()> &create, qint64 expectedBytes) {
        QEventLoop loop;
        QTimer timeout;
        timeout.setSingleShot(true);
        QElapsedTimer elapsed;
        elapsed.start();
        auto transfer = std::unique_ptr<QSshSftpTransfer>(create());
        Sample sample;
        QObject::connect(transfer.get(), &QSshSftpTransfer::finished, &loop, [&] {
            sample.seconds = elapsed.nsecsElapsed() / 1e9;
            loop.quit();
        });
        QObject::connect(&timeout, &QTimer::timeout, &loop, [&] {
            sample.error = QStringLiteral("Benchmark transfer exceeded 120 seconds");
            transfer->cancel();
            loop.quit();
        });
        timeout.start(120000);
        loop.exec();
        if (sample.error.isEmpty() && transfer->state() != QSshSftpTransfer::Succeeded) {
            sample.error = transfer->errorString();
        }
        if (sample.error.isEmpty() && (transfer->bytesTransferred() != expectedBytes ||
                                       transfer->totalBytes() != expectedBytes || sample.seconds <= 0)) {
            sample.error = QStringLiteral("Incorrect byte counts or elapsed time");
        }
        return sample;
    }

    /** 分块校验 path，避免为文件入口额外读取一份完整 payload 到内存。 */
    QByteArray hashFile(const QString &path) {
        QFile file(path);
        if (!file.open(QIODevice::ReadOnly)) {
            return {};
        }
        QCryptographicHash hash(QCryptographicHash::Sha256);
        if (!hash.addData(&file)) {
            return {};
        }
        return hash.result();
    }

    /** 读取正整数配置；缺省时采用 fallback，格式错误由调用方以断言报告。 */
    qint64 setting(const char *name, qint64 fallback) {
        if (!qEnvironmentVariableIsSet(name)) {
            return fallback;
        }
        bool valid = false;
        const auto result = qEnvironmentVariable(name).toLongLong(&valid);
        return valid && result > 0 ? result : -1;
    }

    /** 汇总完整文件传输的平均速率与最快单轮平均速率，不把单个瞬时采样当作峰值。 */
    QJsonObject summarize(const QString &mode, const QString &direction, qint64 size, qint64 window,
                          const QList<double> &seconds) {
        QJsonArray samples;
        QList<double> sorted = seconds;
        std::sort(sorted.begin(), sorted.end());
        double sum = 0;
        for (auto value : seconds) {
            sum += value;
            samples.append(QJsonObject{{"seconds", value}, {"mib_per_second", size / double(MiB) / value}});
        }
        const double median = sorted.size() % 2 ? sorted[sorted.size() / 2]
                                                : (sorted[sorted.size() / 2 - 1] + sorted[sorted.size() / 2]) / 2;
        const double average = size / double(MiB) * seconds.size() / sum;
        const double fastest = size / double(MiB) / sorted.first();
        std::cout << std::left << std::setw(10) << mode.toStdString() << std::setw(10) << direction.toStdString()
                  << std::right << std::setw(10) << window / 1024 << std::fixed << std::setprecision(2) << std::setw(16)
                  << average << std::setw(16) << fastest << std::setw(14) << sum / seconds.size() << std::setw(14)
                  << median << '\n';
        return {{"mode", mode},
                {"direction", direction},
                {"bytes_per_round", double(size)},
                {"window_bytes", double(window)},
                {"rounds", seconds.size()},
                {"average_mib_per_second", average},
                {"fastest_round_mib_per_second", fastest},
                {"mean_seconds", sum / seconds.size()},
                {"median_seconds", median},
                {"fastest_seconds", sorted.first()},
                {"slowest_seconds", sorted.last()},
                {"samples", samples}};
    }
} // namespace

/**
 * 单 worker、真实 SSH/SFTP 回环性能测试，独立可执行文件，不加入普通 CTest 回归。
 * 默认 64 MiB、一次预热、五次计时、4 MiB 窗口，分别测路径和 QBuffer 的上传／下载。
 * 配置：QSSH_SFTP_BENCHMARK_MIB、QSSH_SFTP_BENCHMARK_ROUNDS、QSSH_SFTP_BENCHMARK_WARMUPS、
 * QSSH_SFTP_BENCHMARK_WINDOWS_KIB（逗号分隔）、QSSH_SFTP_BENCHMARK_OUTPUT（可选 JSON 输出）。
 * 本测试使用无分片注入的 Paramiko 服务及系统文件缓存，不声称测得原生服务端或磁盘持久化的上限。
 */
class QSshSftpBenchmark : public QSshIntegration {
protected:
    /** 给此用例的服务端关闭故障注入，保留其他功能测试的服务端行为。 */
    void SetUp() override {
        auto environment = QProcessEnvironment::systemEnvironment();
        environment.insert(QStringLiteral("QSSH_TEST_SFTP_BENCHMARK"), QStringLiteral("1"));
        server.setProcessEnvironment(environment);
        QSshIntegration::SetUp();
    }
};

/** 每轮都验证 SHA-256；各入口、方向和窗口分别预热并统计，不给吞吐设置易波动的通过阈值。 */
TEST_F(QSshSftpBenchmark, UploadAndDownload) {
    const auto sizeMiB = setting("QSSH_SFTP_BENCHMARK_MIB", 64);
    const auto rounds = setting("QSSH_SFTP_BENCHMARK_ROUNDS", 5);
    const auto warmups = setting("QSSH_SFTP_BENCHMARK_WARMUPS", 1);
    ASSERT_GE(sizeMiB, 1);
    ASSERT_LE(sizeMiB, 1024);
    ASSERT_GE(rounds, 1);
    ASSERT_LE(rounds, 100);
    ASSERT_GE(warmups, 1);
    ASSERT_LE(warmups, 10);
    QList<qint64> windows;
    for (const auto &entry : qEnvironmentVariable("QSSH_SFTP_BENCHMARK_WINDOWS_KIB", "4096").split(',')) {
        bool valid = false;
        const auto kib = entry.toLongLong(&valid);
        ASSERT_TRUE(valid);
        ASSERT_GE(kib, 64);
        ASSERT_LE(kib, 1024 * 1024);
        windows.append(kib * 1024);
    }
    const qint64 size = sizeMiB * MiB;
    QByteArray payload(static_cast<int>(size), Qt::Uninitialized);
    quint32 random = 0x4f31a2b7;
    for (int i = 0; i < payload.size(); ++i) {
        random ^= random << 13;
        random ^= random >> 17;
        random ^= random << 5;
        payload[i] = static_cast<char>(random & 255);
    }
    const auto expected = QCryptographicHash::hash(payload, QCryptographicHash::Sha256);
    const auto sourcePath = directory.path() + QStringLiteral("/benchmark-source");
    const auto targetPath = directory.path() + QStringLiteral("/benchmark-target");
    const auto serverPath = directory.path() + QStringLiteral("/sftp-root/benchmark.bin");
    QFile source(sourcePath);
    ASSERT_TRUE(source.open(QIODevice::WriteOnly));
    ASSERT_EQ(source.write(payload), size);
    source.close();
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    ASSERT_EQ(ssh->state(), QSshSession::Ready);
    QJsonArray rows;
    std::cout << "\nSingle Asio worker; loopback Paramiko; " << sizeMiB << " MiB; " << warmups << " warmup(s), "
              << rounds << " measured round(s); MiB/s; SHA-256 checked outside timing\n"
              << "mode      direction windowKiB    averageMiB/s    fastestMiB/s       meanSec     medianSec\n";
    for (const auto window : windows) {
        auto *sftp = ssh->createSftpSession();
        QSshSftpSession::Options sftpOptions;
        sftpOptions.maxInFlightBytes = window;
        sftpOptions.requestTimeoutMs = 120000;
        sftp->open(sftpOptions);
        ASSERT_TRUE(qssh_test::waitUntil(
            [&] { return sftp->state() == QSshSftpSession::Ready || sftp->state() == QSshSftpSession::Closed; }));
        ASSERT_EQ(sftp->state(), QSshSftpSession::Ready) << sftp->errorString().toStdString();
        QSshSftpTransfer::Options transferOptions;
        transferOptions.overwrite = true;
        transferOptions.bufferSize = window;
        transferOptions.idleTimeoutMs = 120000;
        for (const auto mode : {QStringLiteral("path"), QStringLiteral("QBuffer")}) {
            QList<double> uploads, downloads;
            for (int round = -static_cast<int>(warmups); round < rounds; ++round) {
                QBuffer input(&payload);
                ASSERT_TRUE(input.open(QIODevice::ReadOnly));
                const auto sent = measure(
                    [&] {
                        return mode == "path" ? sftp->upload(sourcePath, "/benchmark.bin", transferOptions)
                                              : sftp->upload(&input, "/benchmark.bin", transferOptions);
                    },
                    size);
                ASSERT_TRUE(sent.error.isEmpty()) << sent.error.toStdString();
                ASSERT_EQ(hashFile(serverPath), expected) << "Uploaded bytes differ from source";
                QBuffer output;
                ASSERT_TRUE(output.open(QIODevice::ReadWrite));
                const auto received = measure(
                    [&] {
                        return mode == "path" ? sftp->download("/benchmark.bin", targetPath, transferOptions)
                                              : sftp->download("/benchmark.bin", &output, transferOptions);
                    },
                    size);
                ASSERT_TRUE(received.error.isEmpty()) << received.error.toStdString();
                ASSERT_EQ(mode == "path" ? hashFile(targetPath)
                                         : QCryptographicHash::hash(output.data(), QCryptographicHash::Sha256),
                          expected)
                    << "Downloaded bytes differ from source";
                if (round >= 0) {
                    uploads.append(sent.seconds);
                    downloads.append(received.seconds);
                }
            }
            rows.append(summarize(mode, "upload", size, window, uploads));
            rows.append(summarize(mode, "download", size, window, downloads));
        }
        delete sftp;
    }
    QProcess version;
    version.start(QString::fromUtf8(QSSH_TEST_PYTHON), {"-c", "import paramiko; print(paramiko.__version__)"});
    ASSERT_TRUE(version.waitForFinished(10000));
#ifdef NDEBUG
    const QString build = QStringLiteral("Release");
#else
    const QString build = QStringLiteral("Debug");
#endif
    const QJsonObject report{
        {"timestamp_utc", QDateTime::currentDateTimeUtc().toString(Qt::ISODate)},
        {"platform", QSysInfo::prettyProductName()},
        {"architecture", QSysInfo::currentCpuArchitecture()},
        {"logical_cpus", QThread::idealThreadCount()},
        {"build", build},
        {"qt_version", QT_VERSION_STR},
        {"libssh_version", QString::fromLatin1(ssh_version(0))},
        {"server",
         QStringLiteral("Paramiko %1 on loopback").arg(QString::fromUtf8(version.readAllStandardOutput()).trimmed())},
        {"worker_threads", 1},
        {"warmups_per_mode", double(warmups)},
        {"integrity", QStringLiteral("SHA-256 verified after every upload and download, outside timing")},
        {"timing", QStringLiteral("API call through finished, including open/close and local commit; excluding SSH "
                                  "authentication, warmups and hashes")},
        {"cache", QStringLiteral("System file cache is not flushed; no fsync durability measurement")},
        {"results", rows}};
    const auto outputPath = qEnvironmentVariable("QSSH_SFTP_BENCHMARK_OUTPUT");
    if (!outputPath.isEmpty()) {
        QSaveFile output(outputPath);
        ASSERT_TRUE(output.open(QIODevice::WriteOnly)) << output.errorString().toStdString();
        const auto json = QJsonDocument(report).toJson(QJsonDocument::Indented);
        ASSERT_EQ(output.write(json), json.size());
        ASSERT_TRUE(output.commit());
        std::cout << "JSON: " << outputPath.toStdString() << '\n';
    }
}
