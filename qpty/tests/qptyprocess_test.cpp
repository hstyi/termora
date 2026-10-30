#include <QCoreApplication>
#include <QDir>
#include <QElapsedTimer>
#include <QFileInfo>
#include <QPointer>
#include <QTemporaryDir>
#include <QThread>
#include <QTimer>
#include <functional>
#include <gtest/gtest.h>
#include <qpty/qptyprocess.h>
#include <vector>
#ifdef Q_OS_WIN
#include <windows.h>
#else
#include <fcntl.h>
#include <signal.h>
#include <sys/wait.h>
#include <unistd.h>
#endif

namespace {
    /** 在 timeoutMs 内驱动 Qt 事件循环，直到 condition 成立，不依赖固定等待时长。 */
    bool waitUntil(const std::function<bool()> &condition, int timeoutMs) {
        QElapsedTimer timer;
        timer.start();
        while (!condition() && timer.elapsed() < timeoutMs) {
            QCoreApplication::processEvents(QEventLoop::AllEvents, 10);
            QThread::msleep(2);
        }
        return condition();
    }

    /** 记录 process 的信号，用于 gtest 断言，无需 QtTest 依赖。 */
    struct ProcessEvents {
        int started = 0;                               ///< 收到的成功启动次数。
        int finished = 0;                              ///< 收到的退出次数。
        int errors = 0;                                ///< 收到的错误次数。
        std::vector<QPtyProcess::ProcessState> states; ///< 生命周期通知顺序。

        /** 订阅 process，连接的生命周期由该进程对象管理。 */
        explicit ProcessEvents(QPtyProcess &process) {
            QObject::connect(&process, &QPtyProcess::started, &process, [this] { ++started; });
            QObject::connect(&process, &QPtyProcess::finished, &process, [this] { ++finished; });
            QObject::connect(&process, &QPtyProcess::errorOccurred, &process, [this] { ++errors; });
            QObject::connect(&process, &QPtyProcess::stateChanged, &process,
                             [this](QPtyProcess::ProcessState state) { states.push_back(state); });
        }
    };
} // namespace

/** 使用真实子进程验证各 PTY 后端的参数化 gtest fixture。 */
class QPtyProcessTest : public ::testing::TestWithParam<QPtyProcess::Backend> {
protected:
    /** 将 process 配置为专用子程序，arguments 指定验证模式及参数。 */
    void configure(QPtyProcess &process, const QStringList &arguments) {
        process.setProgram(QString::fromUtf8(QPTY_HELPER_PATH));
        process.setArguments(arguments);
        process.setBackend(GetParam());
    }

    /** 将 process 的新增输出收集到 output，不改变原始字节。 */
    void collect(QPtyProcess &process, QByteArray &output) {
        QObject::connect(&process, &QPtyProcess::readyRead, &process,
                         [&process, &output] { output += process.readAll(); });
    }
};

/** 检查默认值和非法调用不创建子进程。 */
TEST_P(QPtyProcessTest, defaultsAndValidation) {
    QPtyProcess p;
    EXPECT_EQ(p.state(), QPtyProcess::NotRunning);
    EXPECT_EQ(p.terminalSize(), QSize(80, 24));
    EXPECT_EQ(p.processId(), qint64(0));
    ASSERT_TRUE(!p.setTerminalSize({0, 24}));
    ASSERT_TRUE(!p.setTerminalSize({32768, 24}));
    EXPECT_EQ(p.terminalSize(), QSize(80, 24));
    EXPECT_EQ(p.write("data"), qint64(-1));
    EXPECT_EQ(p.error(), QPtyProcess::WriteError);
}

/** 检查真正的 TTY、初始尺寸、Unicode 路径、环境、空参数和 Windows 引号规则。 */
TEST_P(QPtyProcessTest, terminalAndConfiguration) {
    QTemporaryDir temporary;
    ASSERT_TRUE(temporary.isValid());
    const QString directory = temporary.path() + QString::fromUtf8("/工作 目录");
    ASSERT_TRUE(QDir().mkpath(directory));
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    const QStringList arguments = {QString(), QStringLiteral("two words"), QStringLiteral("a\"b\\"),
                                   QString::fromUtf8("中文参数")};
    configure(p, QStringList{QStringLiteral("report")} + arguments);
    p.setWorkingDirectory(directory);
    auto environment = QProcessEnvironment::systemEnvironment();
    environment.insert(QStringLiteral("QPTY_TEST_VALUE"), QString::fromUtf8("环境 value"));
    p.setProcessEnvironment(environment);
    ASSERT_TRUE(p.setTerminalSize({200, 35}));
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 15000)) << p.errorString().toStdString();
    EXPECT_EQ(events.started, 1);
    EXPECT_EQ(p.exitCode(), 0);
    ASSERT_TRUE(output.contains("TTY=111")) << output.constData();
    ASSERT_TRUE(output.contains("SIZE=200,35")) << output.constData();
    ASSERT_TRUE(output.contains("CWD=" + QFileInfo(directory).canonicalFilePath().toUtf8().toBase64()));
    ASSERT_TRUE(output.contains("ENV=" + QString::fromUtf8("环境 value").toUtf8().toBase64()));
    for (const auto &argument : arguments) {
        ASSERT_TRUE(output.contains("ARG=" + argument.toUtf8().toBase64()));
    }
    ASSERT_TRUE(output.contains("STDERR-MARKER"));
    ASSERT_TRUE(p.activeBackend() != QPtyProcess::AutoBackend);
    EXPECT_EQ(p.error(), QPtyProcess::NoError);
}

/** 检查交互读写与活动会话尺寸调整。 */
TEST_P(QPtyProcessTest, interactiveAndResize) {
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    configure(p, {"interactive"});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return output.contains("READY"); }, 15000)) << p.errorString().toStdString();
    ASSERT_TRUE(p.processId() > 0);
    ASSERT_TRUE(p.setTerminalSize({90, 31}));
    // 多次询问直到后台尺寸请求生效，不依赖固定 sleep 或命令/resize 的跨线程竞态。
    QTimer query;
    QObject::connect(&query, &QTimer::timeout, &p, [&p] { p.write("size\r"); });
    query.start(50);
    ASSERT_TRUE(waitUntil([&] { return output.contains("SIZE=90,31"); }, 5000)) << p.errorString().toStdString();
    query.stop();
    const QByteArray text = QString::fromUtf8("你好 PTY").toUtf8();
    EXPECT_EQ(p.write(text + "\r"), qint64(text.size() + 1));
    ASSERT_TRUE(waitUntil([&] { return output.contains("ECHO=" + text.toBase64()); }, 5000))
        << p.errorString().toStdString();
    p.write("quit\r");
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 10000)) << p.errorString().toStdString();
    ASSERT_TRUE(output.contains("BYE"));
    EXPECT_EQ(p.exitStatus(), QPtyProcess::NormalExit);
    EXPECT_EQ(p.bytesToWrite(), qint64(0));
}

/** 检查快速退出的退出码、状态顺序和未主动读取的最终输出。 */
TEST_P(QPtyProcessTest, exitAndBufferedTail) {
    QPtyProcess p;
    configure(p, {"exit", "7"});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 15000)) << p.errorString().toStdString();
    EXPECT_EQ(events.started, 1);
    ASSERT_EQ(events.states.size(), 3);
    EXPECT_EQ(events.states.at(0), QPtyProcess::Starting);
    EXPECT_EQ(events.states.at(1), QPtyProcess::Running);
    EXPECT_EQ(events.states.at(2), QPtyProcess::Stopped);
    EXPECT_EQ(p.exitCode(), 7);
    EXPECT_EQ(p.exitStatus(), QPtyProcess::NormalExit);
    ASSERT_TRUE(p.bytesAvailable() > 0);
    ASSERT_TRUE(p.readAll().contains("FINAL-OUTPUT"));
    EXPECT_EQ(p.bytesAvailable(), qint64(0));
}

/** 启动失败即进入终态，修改配置并重试不能创建第二次会话。 */
TEST_P(QPtyProcessTest, failedStartIsFinal) {
    QPtyProcess p;
    configure(p, {});
    p.setProgram(QStringLiteral("/qpty/nonexistent/executable"));
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.errors == 1; }, 10000)) << p.errorString().toStdString();
    EXPECT_EQ(p.state(), QPtyProcess::Stopped);
    EXPECT_EQ(p.error(), QPtyProcess::FailedToStart);
    ASSERT_TRUE(!p.errorString().isEmpty());
    EXPECT_EQ(events.started, 0);
    EXPECT_EQ(events.finished, 0);
    const QString error = p.errorString();
    configure(p, {"exit", "0"});
    p.start();
    EXPECT_EQ(p.state(), QPtyProcess::Stopped);
    EXPECT_EQ(p.error(), QPtyProcess::FailedToStart);
    EXPECT_EQ(p.errorString(), error);
    EXPECT_EQ(events.started, 0);
    EXPECT_EQ(events.errors, 1);
    EXPECT_EQ(events.finished, 0);
}

/** 不存在的工作目录和嵌入 NUL 的参数必须失败，分别使用一次性进程对象。 */
TEST_P(QPtyProcessTest, invalidConfiguration) {
    for (const bool invalidDirectory : {true, false}) {
        QPtyProcess p;
        configure(p, {"exit", "0"});
        if (invalidDirectory) {
            p.setWorkingDirectory(QStringLiteral("/qpty/directory/does-not-exist"));
        } else {
            p.setArguments({QString::fromLatin1("a\0b", 3)});
        }
        ProcessEvents events = ProcessEvents(p);
        p.start();
        ASSERT_TRUE(waitUntil([&] { return events.errors == 1; }, 10000)) << p.errorString().toStdString();
        EXPECT_EQ(p.error(), QPtyProcess::FailedToStart);
        EXPECT_EQ(p.state(), QPtyProcess::Stopped);
        p.start();
        EXPECT_EQ(p.state(), QPtyProcess::Stopped);
        EXPECT_EQ(events.errors, 1);
    }
}

/** 大输出不会堵住子进程，且 Unix 原始字节不会在快速退出时丢失。 */
TEST_P(QPtyProcessTest, largeOutput) {
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    configure(p, {"bulk"});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 20000)) << p.errorString().toStdString();
    ASSERT_TRUE(output.contains("BULK-END"));
#ifndef Q_OS_WIN
    EXPECT_EQ(output.count('Z'), 1024 * 1024);
#endif
    EXPECT_EQ(p.exitCode(), 0);
}

/** 大输入要处理短写，并允许 QObject 事件循环在发送期间继续运行。 */
TEST_P(QPtyProcessTest, largeInput) {
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    configure(p, {"receive", "262144"});
    ProcessEvents events = ProcessEvents(p);
    qint64 written = 0;
    QObject::connect(&p, &QPtyProcess::bytesWritten, &p, [&written](qint64 count) { written += count; });
    p.start();
    ASSERT_TRUE(waitUntil([&] { return output.contains("READY"); }, 15000)) << p.errorString().toStdString();
    EXPECT_EQ(p.write(QByteArray(262144, 'x')), qint64(262144));
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 20000)) << p.errorString().toStdString();
    ASSERT_TRUE(output.contains("RECEIVED=262144")) << output.constData();
    EXPECT_EQ(written, qint64(262144));
    EXPECT_EQ(p.exitCode(), 0);
}

/** 发送队列有明确上限，拒绝超限写入后会话仍可使用。 */
TEST_P(QPtyProcessTest, writeBackpressure) {
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    configure(p, {"interactive"});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return output.contains("READY"); }, 15000)) << p.errorString().toStdString();
    EXPECT_EQ(p.write(QByteArray(4 * 1024 * 1024 + 1, 'x')), qint64(-1));
    EXPECT_EQ(p.bytesToWrite(), qint64(0));
    ASSERT_TRUE(p.write("quit\r") > 0);
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 10000)) << p.errorString().toStdString();
}

/** 强制结束必须有且只有一次 finished，并永久停留在 Stopped。 */
TEST_P(QPtyProcessTest, killRunningProcess) {
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    configure(p, {"interactive"});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return output.contains("READY"); }, 15000)) << p.errorString().toStdString();
    p.start(); // 活动会话的重复 start 不应创建第二个进程。
    p.kill();
    p.kill();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 15000)) << p.errorString().toStdString();
    EXPECT_EQ(p.exitStatus(), QPtyProcess::CrashExit);
    EXPECT_EQ(p.processId(), qint64(0));
    p.start();
    EXPECT_EQ(p.state(), QPtyProcess::Stopped);
    EXPECT_EQ(events.started, 1);
    EXPECT_EQ(events.finished, 1);
}

/** Starting 信号中的停止请求不得因后台线程尚未启动而丢失。 */
TEST_P(QPtyProcessTest, killWhileStarting) {
    QPtyProcess p;
    configure(p, {"interactive"});
    QObject::connect(&p, &QPtyProcess::stateChanged, &p, [&p](QPtyProcess::ProcessState state) {
        if (state == QPtyProcess::Starting) {
            p.kill();
        }
    });
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 15000)) << p.errorString().toStdString();
    EXPECT_EQ(p.exitStatus(), QPtyProcess::CrashExit);
}

/** terminate 与 kill 都必须能回收会话；Unix 验证 SIGTERM 的退出编码。 */
TEST_P(QPtyProcessTest, terminateRunningProcess) {
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    configure(p, {"interactive"});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return output.contains("READY"); }, 15000)) << p.errorString().toStdString();
    p.terminate();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 15000)) << p.errorString().toStdString();
    p.start();
    EXPECT_EQ(p.state(), QPtyProcess::Stopped);
    EXPECT_EQ(events.started, 1);
#ifndef Q_OS_WIN
    EXPECT_EQ(p.exitCode(), 128 + SIGTERM);
#endif
}

/** 析构活动对象要结束并回收原生子进程。 */
TEST_P(QPtyProcessTest, destructionReapsProcess) {
    qint64 pid = 0;
#ifdef Q_OS_WIN
    HANDLE handle = nullptr;
#endif
    {
        QPtyProcess p;
        QByteArray output;
        collect(p, output);
        configure(p, {"interactive"});
        p.start();
        ASSERT_TRUE(waitUntil([&] { return output.contains("READY"); }, 15000)) << p.errorString().toStdString();
        pid = p.processId();
#ifdef Q_OS_WIN
        handle = OpenProcess(SYNCHRONIZE, FALSE, static_cast<DWORD>(pid));
        ASSERT_TRUE(handle);
#endif
    }
#ifdef Q_OS_WIN
    EXPECT_EQ(WaitForSingleObject(handle, 0), DWORD(WAIT_OBJECT_0));
    CloseHandle(handle);
#else
    EXPECT_EQ(::kill(static_cast<pid_t>(pid), 0), -1);
    EXPECT_EQ(errno, ESRCH);
    EXPECT_EQ(waitpid(static_cast<pid_t>(pid), nullptr, WNOHANG), pid_t(-1));
    EXPECT_EQ(errno, ECHILD);
#endif
}

/** 首次启动之前请求停止，也会封闭生命周期；不应发出启动或退出通知。 */
TEST_P(QPtyProcessTest, stopBeforeStartIsFinal) {
    for (const bool force : {true, false}) {
        QPtyProcess p;
        configure(p, {"exit", "0"});
        ProcessEvents events = ProcessEvents(p);
        if (force) {
            p.kill();
        } else {
            p.terminate();
        }
        EXPECT_EQ(p.state(), QPtyProcess::Stopped);
        p.start();
        p.terminate();
        p.kill();
        EXPECT_EQ(p.state(), QPtyProcess::Stopped);
        EXPECT_EQ(events.started, 0);
        EXPECT_EQ(events.finished, 0);
        ASSERT_EQ(events.states.size(), 1);
        EXPECT_EQ(events.states.front(), QPtyProcess::Stopped);
    }
}

#ifndef Q_OS_WIN
/** PTY 子进程不能继承宿主未设置 CLOEXEC 的业务描述符。 */
TEST_P(QPtyProcessTest, closesInheritedDescriptors) {
    const int original = ::open("/dev/null", O_RDONLY);
    ASSERT_TRUE(original >= 0);
    const int high = fcntl(original, F_DUPFD, 200);
    ::close(original);
    ASSERT_TRUE(high >= 0);
    QPtyProcess p;
    configure(p, {"fd", QString::number(high)});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 10000)) << p.errorString().toStdString();
    ::close(high);
    EXPECT_EQ(p.exitCode(), 0);
}
#endif

#ifndef Q_OS_WIN
/** Ctrl+C 经终端行规程生成 SIGINT，而不是被宿主解释为普通文本。 */
TEST_P(QPtyProcessTest, terminalInterrupt) {
    QPtyProcess p;
    QByteArray output;
    collect(p, output);
    configure(p, {"interactive"});
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return output.contains("READY"); }, 10000)) << p.errorString().toStdString();
    p.write(QByteArray(1, '\3'));
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 10000)) << p.errorString().toStdString();
    EXPECT_EQ(p.exitCode(), 128 + SIGINT);
}
#endif

/** PATH 查找必须使用配置给子进程的环境，而非宿主当前的 PATH。 */
TEST_P(QPtyProcessTest, configuredPathLookup) {
    QPtyProcess p;
    configure(p, {"exit", "0"});
    const QFileInfo executable = QFileInfo(p.program());
    p.setProgram(executable.fileName());
    auto environment = QProcessEnvironment::systemEnvironment();
    environment.insert(QStringLiteral("PATH"), executable.absolutePath());
    p.setProcessEnvironment(environment);
    QTemporaryDir directory;
    ASSERT_TRUE(directory.isValid());
    p.setWorkingDirectory(directory.path());
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1 || events.errors; }, 15000));
    EXPECT_EQ(events.errors, 0) << p.errorString().toStdString();
    EXPECT_EQ(events.finished, 1);
    EXPECT_EQ(p.exitCode(), 0);
}

/** 不支持的后端必须明确失败，不能意外执行另一个后端。 */
TEST_P(QPtyProcessTest, unsupportedBackend) {
    QPtyProcess p;
    configure(p, {"exit", "0"});
#ifdef Q_OS_WIN
    p.setBackend(QPtyProcess::UnixPty);
#else
    p.setBackend(QPtyProcess::ConPty);
#endif
    ProcessEvents events = ProcessEvents(p);
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.errors == 1; }, 10000));
    EXPECT_EQ(p.error(), QPtyProcess::UnsupportedBackend);
    EXPECT_EQ(events.started, 0);
    EXPECT_EQ(events.finished, 0);
}

/** readyRead 槽中删除进程对象时，后台 I/O 不得重入已释放的 QObject。 */
TEST_P(QPtyProcessTest, deleteFromReadyRead) {
    QPointer<QPtyProcess> process = new QPtyProcess;
    configure(*process, {"interactive"});
    QObject::connect(process, &QPtyProcess::readyRead, process, [process] { delete process.data(); });
    process->start();
    ASSERT_TRUE(waitUntil([&] { return process.isNull(); }, 15000));
}

/** Stopped/finished 槽中的重启请求无效，首次会话的退出码和尾部数据保持可读。 */
TEST_P(QPtyProcessTest, restartFromFinalSignalsIsIgnored) {
    QPtyProcess p;
    configure(p, {"exit", "7"});
    ProcessEvents events = ProcessEvents(p);
    QObject::connect(&p, &QPtyProcess::stateChanged, &p, [&p](QPtyProcess::ProcessState state) {
        if (state == QPtyProcess::Stopped) {
            p.start();
        }
    });
    QObject::connect(&p, &QPtyProcess::finished, &p, [&p] {
        p.setArguments({"exit", "5"});
        p.start();
    });
    p.start();
    ASSERT_TRUE(waitUntil([&] { return events.finished == 1; }, 15000));
    EXPECT_EQ(events.started, 1);
    EXPECT_EQ(p.state(), QPtyProcess::Stopped);
    EXPECT_EQ(p.exitCode(), 7);
    EXPECT_TRUE(p.readAll().contains("FINAL-OUTPUT"));
}

/** 返回当前机器可实跑的后端；Windows 7 不包含 ConPTY。 */
std::vector<QPtyProcess::Backend> availableBackends() {
    std::vector<QPtyProcess::Backend> backends = {QPtyProcess::AutoBackend};
#ifdef Q_OS_WIN
    if (GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "CreatePseudoConsole")) {
        backends.push_back(QPtyProcess::ConPty);
    }
    backends.push_back(QPtyProcess::WinPty);
#else
    backends.push_back(QPtyProcess::UnixPty);
#endif
    return backends;
}

INSTANTIATE_TEST_SUITE_P(Backends, QPtyProcessTest, ::testing::ValuesIn(availableBackends()));

/** 为 argc/argv 指定的 gtest 运行建立 Qt 事件分发器。 */
int main(int argc, char **argv) {
    QCoreApplication application = QCoreApplication(argc, argv);
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
