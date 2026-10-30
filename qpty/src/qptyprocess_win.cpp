#include "qptyprocess_p.h"

#include <QDir>
#include <QFileInfo>
#include <QStringList>
#include <algorithm>
#include <utility>
#include <vector>
#include <windows.h>
#include <winpty.h>

namespace {
    /** Windows 7 SDK 也能使用的伪控制台属性值，不静态导入新系统 API。 */
    constexpr DWORD_PTR KPseudoConsoleAttribute = ProcThreadAttributeValue(22, FALSE, TRUE, FALSE);
    /** 提供旧 Windows SDK 也能声明的 ConPTY 句柄，不静态导入新系统函数。 */
    using PseudoConsole = void *;
    /** CreatePseudoConsole 的运行时函数签名，参数与 Windows SDK 一致。 */
    using CreateConsoleFn = HRESULT(WINAPI *)(COORD, HANDLE, HANDLE, DWORD, PseudoConsole *);
    /** ResizePseudoConsole 的运行时函数签名。 */
    using ResizeConsoleFn = HRESULT(WINAPI *)(PseudoConsole, COORD);
    /** ClosePseudoConsole 的运行时函数签名。 */
    using CloseConsoleFn = void(WINAPI *)(PseudoConsole);

    /** 将 code 转为 operation 的可读错误信息。 */
    QString windowsError(const char *operation, DWORD code) {
        wchar_t *text = nullptr;
        FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                       nullptr, code, 0, reinterpret_cast<wchar_t *>(&text), 0, nullptr);
        const QString message = text ? QString::fromWCharArray(text).trimmed()
                                     : QStringLiteral("Windows error %1").arg(code);
        if (text) {
            LocalFree(text);
        }
        return QString::fromLatin1(operation) + QStringLiteral(": ") + message;
    }

    /** 取出 error 的 winpty 描述并释放错误对象；fallback 用于未返回错误对象的异常情况。 */
    QString takeWinPtyError(winpty_error_ptr_t error, const char *fallback) {
        const QString message = error ? QString::fromWCharArray(winpty_error_msg(error))
                                      : QString::fromLatin1(fallback);
        if (error) {
            winpty_error_free(error);
        }
        return message;
    }

    /** 关闭 handle 并置空，兼容 CreateFile 返回的 INVALID_HANDLE_VALUE。 */
    void closeHandle(HANDLE &handle) {
        if (handle && handle != INVALID_HANDLE_VALUE) {
            CloseHandle(handle);
        }
        handle = nullptr;
    }

    /** 按 Windows CRT 规则引用 argument，保留空参数、引号以及引号前的反斜杠。 */
    QString quoteArgument(const QString &argument) {
        QString result = QString(QLatin1Char('"'));
        int slashes = 0;
        for (const QChar character : argument) {
            if (character == QLatin1Char('\\')) {
                ++slashes;
                continue;
            }
            if (character == QLatin1Char('"')) {
                result += QString(slashes * 2 + 1, QLatin1Char('\\'));
            } else {
                result += QString(slashes, QLatin1Char('\\'));
            }
            slashes = 0;
            result += character;
        }
        result += QString(slashes * 2, QLatin1Char('\\'));
        result += QLatin1Char('"');
        return result;
    }

    /** 在 options 的工作目录和 PATH 中查找可执行文件，不通过宿主环境隐式解析。 */
    QString executablePath(const QPtyProcessOptions &options) {
        QStringList directories;
        if (options.program.contains(QLatin1Char('/')) || options.program.contains(QLatin1Char('\\')) ||
            QFileInfo(options.program).isAbsolute()) {
            directories << options.directory;
        } else {
            directories = QStringList{options.directory} +
                          options.environment.value(QStringLiteral("PATH")).split(QLatin1Char(';'), Qt::SkipEmptyParts);
        }
        for (const auto &directory : directories) {
            QString candidate = QDir(QDir(options.directory).absoluteFilePath(directory))
                                    .absoluteFilePath(options.program);
            if (QFileInfo(candidate).suffix().isEmpty()) {
                candidate += QStringLiteral(".exe");
            }
            if (QFileInfo(candidate).isFile()) {
                return QDir::toNativeSeparators(candidate);
            }
        }
        return {};
    }

    /** 将 environment 编码成 CreateProcessW/winpty 使用的双 NUL 终止 UTF-16 环境块。 */
    std::wstring environmentBlock(const QProcessEnvironment &environment) {
        auto entries = environment.toStringList();
        std::sort(entries.begin(), entries.end(), [](const QString &left, const QString &right) {
            return QString::compare(left, right, Qt::CaseInsensitive) < 0;
        });
        std::wstring result;
        for (const auto &entry : entries) {
            result += entry.toStdWString();
            result.push_back(L'\0');
        }
        if (result.empty()) {
            result.push_back(L'\0');
        }
        result.push_back(L'\0');
        return result;
    }

    /** Windows 会话后端：控制、读取、写入各有独立线程，防止同步管道互相阻塞。 */
    class WindowsPtyBackend final : public QPtyProcessBackend {
    public:
        /** 使用 owner/options 绑定公共通知与配置。 */
        WindowsPtyBackend(QPtyProcessPrivate *owner, QPtyProcessOptions options)
            : QPtyProcessBackend(owner, std::move(options)) {}

        /** 回收所有线程后关闭控制事件。 */
        ~WindowsPtyBackend() override {
            shutdown();
            closeHandle(commandEvent);
        }

        /** 建立控制事件并启动后台会话。 */
        void launch() override {
            commandEvent = CreateEventW(nullptr, FALSE, FALSE, nullptr);
            if (!commandEvent) {
                failed(windowsError("CreateEvent", GetLastError()));
                return;
            }
            worker = std::thread([this] { run(); });
        }

        /** 强制结束活动会话并等待原生资源关闭。 */
        void shutdown() override {
            stop(true);
            if (worker.joinable()) {
                worker.join();
            }
        }

    private:
        /** 唤醒控制线程处理终止或尺寸变更。 */
        void wake() override {
            if (commandEvent) {
                SetEvent(commandEvent);
            }
        }

        /** 检测 API、启动程序并监控控制请求及进程退出。 */
        void run();
        /** 通过 ConPTY 启动 app，command 为可修改命令行，cwd/env 为 UTF-16 配置；失败写入 message。 */
        bool startConPty(const std::wstring &app, std::wstring &command, const std::wstring &cwd, std::wstring &env,
                         QString &message);
        /** 通过 winpty 启动 app/command/cwd/env 指定的程序，失败写入 message。 */
        bool startWinPty(const std::wstring &app, const std::wstring &command, const std::wstring &cwd,
                         const std::wstring &env, QString &message);
        /** 停止 I/O 并释放控制台；drainLegacy 表示正常退出时先等待 winpty 排空最终屏幕。 */
        void cleanup(bool drainLegacy);

        /** 取消 thread 的同步 I/O 并等待退出，随后关闭线程句柄。 */
        void cancelAndJoin(HANDLE &thread) {
            if (!thread) {
                return;
            }
            // 反复取消可覆盖“设置停止标记后，线程尚未进入 ReadFile/WriteFile”的竞态。
            while (WaitForSingleObject(thread, 10) == WAIT_TIMEOUT) {
                CancelSynchronousIo(thread);
            }
            closeHandle(thread);
        }

        /** 在独立线程读取 self 对应的终端，直到 EOF 或取消。 */
        static DWORD WINAPI readLoop(void *self);
        /** 在独立线程将 self 的发送队列写入同步输入管道。 */
        static DWORD WINAPI writeLoop(void *self);

        std::thread worker;                         ///< 负责创建、调整、等待和释放控制台的线程。
        HANDLE commandEvent = nullptr;              ///< 唤醒控制线程的自动重置事件。
        HANDLE inputPipe = nullptr;                 ///< 宿主向终端写入的同步管道。
        HANDLE outputPipe = nullptr;                ///< 宿主从终端读取的同步管道。
        HANDLE process = nullptr;                   ///< 保持打开直至回收结束，防止 PID 被复用。
        HANDLE job = nullptr;                       ///< ConPTY 子进程组的 kill-on-close Job。
        HANDLE reader = nullptr;                    ///< 读线程句柄，供 CancelSynchronousIo 使用。
        HANDLE writer = nullptr;                    ///< 写线程句柄，供 CancelSynchronousIo 使用。
        PseudoConsole console = nullptr;            ///< 活动 ConPTY 句柄。
        winpty_t *legacy = nullptr;                 ///< 活动 winpty 会话；仅控制线程访问其 RPC API。
        CreateConsoleFn createConsole = nullptr;    ///< 从 kernel32 运行时解析的创建函数。
        ResizeConsoleFn resizeConsole = nullptr;    ///< 从 kernel32 运行时解析的调整函数。
        CloseConsoleFn closeConsole = nullptr;      ///< 从 kernel32 运行时解析的关闭函数。
        std::atomic<bool> readerStopping = {false}; ///< 输出排空后才停止读线程。
        std::atomic<bool> writerStopping = {false}; ///< 关闭会话时立即停止写线程。
    };

    bool WindowsPtyBackend::startConPty(const std::wstring &app, std::wstring &command, const std::wstring &cwd,
                                        std::wstring &env, QString &message) {
        HANDLE consoleInput = nullptr, consoleOutput = nullptr;
        if (!CreatePipe(&consoleInput, &inputPipe, nullptr, 65536) ||
            !CreatePipe(&outputPipe, &consoleOutput, nullptr, 65536)) {
            message = windowsError("CreatePipe", GetLastError());
            closeHandle(consoleInput);
            closeHandle(consoleOutput);
            return false;
        }
        const COORD size = {static_cast<SHORT>(options.size.width()), static_cast<SHORT>(options.size.height())};
        const HRESULT result = createConsole(size, consoleInput, consoleOutput, 0, &console);
        closeHandle(consoleInput);
        closeHandle(consoleOutput);
        if (FAILED(result)) {
            message = windowsError("CreatePseudoConsole", static_cast<DWORD>(result));
            return false;
        }
        SIZE_T bytes = 0;
        InitializeProcThreadAttributeList(nullptr, 1, 0, &bytes);
        std::vector<unsigned char> storage = std::vector<unsigned char>(bytes);
        auto *attributes = reinterpret_cast<LPPROC_THREAD_ATTRIBUTE_LIST>(storage.data());
        if (!InitializeProcThreadAttributeList(attributes, 1, 0, &bytes)) {
            message = windowsError("InitializeProcThreadAttributeList", GetLastError());
            return false;
        }
        if (!UpdateProcThreadAttribute(attributes, 0, KPseudoConsoleAttribute, console, sizeof(console), nullptr,
                                       nullptr)) {
            message = windowsError("UpdateProcThreadAttribute", GetLastError());
            DeleteProcThreadAttributeList(attributes);
            return false;
        }
        STARTUPINFOEXW startup = {};
        startup.StartupInfo.cb = sizeof(startup);
        startup.lpAttributeList = attributes;
        PROCESS_INFORMATION information = {};
        const BOOL created = CreateProcessW(app.c_str(), command.data(), nullptr, nullptr, FALSE,
                                            EXTENDED_STARTUPINFO_PRESENT | CREATE_UNICODE_ENVIRONMENT |
                                                CREATE_SUSPENDED,
                                            env.data(), cwd.c_str(), &startup.StartupInfo, &information);
        const DWORD creationError = GetLastError();
        DeleteProcThreadAttributeList(attributes);
        if (!created) {
            message = windowsError("CreateProcess", creationError);
            return false;
        }
        process = information.hProcess;
        job = CreateJobObjectW(nullptr, nullptr);
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits = {};
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        if (!job || !SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits)) ||
            !AssignProcessToJobObject(job, process)) {
            message = windowsError("Assign PTY process job", GetLastError());
            TerminateProcess(process, 1);
            closeHandle(information.hThread);
            return false;
        }
        if (ResumeThread(information.hThread) == static_cast<DWORD>(-1)) {
            message = windowsError("ResumeThread", GetLastError());
            closeHandle(information.hThread);
            return false;
        }
        closeHandle(information.hThread);
        return true;
    }

    bool WindowsPtyBackend::startWinPty(const std::wstring &app, const std::wstring &command, const std::wstring &cwd,
                                        const std::wstring &env, QString &message) {
        winpty_error_ptr_t error = nullptr;
        auto *config = winpty_config_new(0, &error);
        if (!config) {
            message = takeWinPtyError(error, "winpty_config_new failed");
            return false;
        }
        winpty_config_set_initial_size(config, options.size.width(), options.size.height());
        winpty_config_set_agent_timeout(config, 5000);
        legacy = winpty_open(config, &error);
        winpty_config_free(config);
        if (!legacy) {
            message = takeWinPtyError(error, "winpty_open failed; deploy winpty-agent.exe");
            return false;
        }
        inputPipe = CreateFileW(winpty_conin_name(legacy), GENERIC_WRITE, 0, nullptr, OPEN_EXISTING, 0, nullptr);
        outputPipe = CreateFileW(winpty_conout_name(legacy), GENERIC_READ, 0, nullptr, OPEN_EXISTING, 0, nullptr);
        if (inputPipe == INVALID_HANDLE_VALUE || outputPipe == INVALID_HANDLE_VALUE) {
            message = windowsError("Connect winpty pipe", GetLastError());
            return false;
        }
        auto *spawn = winpty_spawn_config_new(WINPTY_SPAWN_FLAG_AUTO_SHUTDOWN | WINPTY_SPAWN_FLAG_EXIT_AFTER_SHUTDOWN,
                                              app.c_str(), command.c_str(), cwd.c_str(), env.c_str(), &error);
        if (!spawn) {
            message = takeWinPtyError(error, "winpty_spawn_config_new failed");
            return false;
        }
        DWORD code = 0;
        const BOOL created = winpty_spawn(legacy, spawn, &process, nullptr, &code, &error);
        winpty_spawn_config_free(spawn);
        if (!created) {
            message = takeWinPtyError(error, "winpty_spawn failed");
            return false;
        }
        return true;
    }

    DWORD WINAPI WindowsPtyBackend::readLoop(void *self) {
        auto *backend = static_cast<WindowsPtyBackend *>(self);
        while (!backend->readerStopping.load()) {
            char buffer[16384];
            DWORD count = 0;
            if (!ReadFile(backend->outputPipe, buffer, sizeof(buffer), &count, nullptr)) {
                const DWORD error = GetLastError();
                if (error != ERROR_BROKEN_PIPE && error != ERROR_OPERATION_ABORTED && !backend->readerStopping.load()) {
                    backend->reportError(QPtyProcess::ReadError, windowsError("Read PTY pipe", error));
                    backend->stop(true);
                }
                break;
            }
            if (!count) {
                break;
            }
            backend->output(QByteArray(buffer, static_cast<int>(count)));
        }
        return 0;
    }

    DWORD WINAPI WindowsPtyBackend::writeLoop(void *self) {
        auto *backend = static_cast<WindowsPtyBackend *>(self);
        while (!backend->writerStopping.load()) {
            QByteArray data;
            {
                std::unique_lock<std::mutex> lock = std::unique_lock<std::mutex>(backend->mutex);
                backend->inputReady.wait(
                    lock, [backend] { return backend->writerStopping.load() || !backend->pendingInput.isEmpty(); });
                if (backend->writerStopping.load()) {
                    break;
                }
                data = std::exchange(backend->pendingInput, {});
            }
            int offset = 0;
            while (offset < data.size() && !backend->writerStopping.load()) {
                DWORD count = 0;
                if (!WriteFile(backend->inputPipe, data.constData() + offset,
                               std::min<int>(16384, data.size() - offset), &count, nullptr) ||
                    !count) {
                    if (!backend->writerStopping.load()) {
                        backend->reportError(QPtyProcess::WriteError, windowsError("Write PTY pipe", GetLastError()));
                        backend->stop(true);
                    }
                    return 0;
                }
                offset += count;
                backend->written(count);
            }
        }
        return 0;
    }

    void WindowsPtyBackend::cleanup(bool drainLegacy) {
        // ConPTY 的关闭可能产生最后的输出，读线程必须一直保持工作到控制台关闭后。
        writerStopping.store(true);
        inputReady.notify_all();
        cancelAndJoin(writer);
        closeHandle(inputPipe);
        if (legacy) {
            if (drainLegacy && reader && WaitForSingleObject(reader, 5000) == WAIT_TIMEOUT) {
                reportError(QPtyProcess::ReadError, QStringLiteral("Timed out draining winpty output"));
            }
            winpty_free(legacy);
            legacy = nullptr;
        }
        if (console) {
            if (!reader) {
                closeHandle(outputPipe);
            }
            closeConsole(console);
            console = nullptr;
        }
        closeHandle(job);
        if (reader) {
            // 控制台关闭后输出管道应到达 EOF；异常代理不会无限阻塞析构。
            WaitForSingleObject(reader, 5000);
            readerStopping.store(true);
            cancelAndJoin(reader);
        }
        closeHandle(outputPipe);
        if (process) {
            WaitForSingleObject(process, 5000);
        }
        closeHandle(process);
    }

    void WindowsPtyBackend::run() {
        const HMODULE kernel = GetModuleHandleW(L"kernel32.dll");
        createConsole = reinterpret_cast<CreateConsoleFn>(GetProcAddress(kernel, "CreatePseudoConsole"));
        resizeConsole = reinterpret_cast<ResizeConsoleFn>(GetProcAddress(kernel, "ResizePseudoConsole"));
        closeConsole = reinterpret_cast<CloseConsoleFn>(GetProcAddress(kernel, "ClosePseudoConsole"));
        const bool conPtyAvailable = createConsole && resizeConsole && closeConsole;
        auto selected = options.backend;
        if (selected == QPtyProcess::AutoBackend) {
            selected = conPtyAvailable ? QPtyProcess::ConPty : QPtyProcess::WinPty;
        }
        if ((selected != QPtyProcess::ConPty && selected != QPtyProcess::WinPty) ||
            (selected == QPtyProcess::ConPty && !conPtyAvailable)) {
            failed(QStringLiteral("Requested PTY backend is unavailable on this Windows version"),
                   QPtyProcess::UnsupportedBackend);
            return;
        }
        const QString executable = executablePath(options);
        if (executable.isEmpty()) {
            failed(QStringLiteral("Executable not found: %1").arg(options.program));
            return;
        }
        QString line = quoteArgument(executable);
        for (const auto &argument : options.arguments) {
            line += QLatin1Char(' ') + quoteArgument(argument);
        }
        auto command = line.toStdWString();
        auto env = environmentBlock(options.environment);
        const auto app = executable.toStdWString();
        const auto cwd = QDir::toNativeSeparators(options.directory).toStdWString();
        QString message;
        const bool success = selected == QPtyProcess::ConPty ? startConPty(app, command, cwd, env, message)
                                                             : startWinPty(app, command, cwd, env, message);
        if (!success) {
            if (process) {
                TerminateProcess(process, 1);
            }
            cleanup(false);
            failed(message);
            return;
        }
        bool forced = false; // 记录强制终止，用于区分最终退出状态。
        // started 必须先于 readyRead 排队，即使子进程启动后立即输出并退出。
        started(GetProcessId(process), selected);
        reader = CreateThread(nullptr, 0, readLoop, this, 0, nullptr);
        writer = CreateThread(nullptr, 0, writeLoop, this, 0, nullptr);
        if (!reader || !writer) {
            reportError(QPtyProcess::UnknownError, windowsError("Create PTY I/O thread", GetLastError()));
            forced = true;
            TerminateProcess(process, 1);
        }
        wake(); // 处理在 Starting 状态、控制事件创建之前收到的请求。
        bool closedByRequest = false;
        while (!forced) {
            HANDLE handles[2] = {process, commandEvent};
            const DWORD result = WaitForMultipleObjects(2, handles, FALSE, INFINITE);
            if (result == WAIT_OBJECT_0) {
                break;
            }
            if (result == WAIT_FAILED) {
                reportError(QPtyProcess::UnknownError, windowsError("Wait for PTY process", GetLastError()));
                forced = true;
                TerminateProcess(process, 1);
                break;
            }
            int stop;
            bool resize;
            QSize size;
            {
                std::lock_guard<std::mutex> lock = std::lock_guard<std::mutex>(mutex);
                stop = stopRequest;
                resize = std::exchange(resizePending, false);
                size = requestedSize;
            }
            if (stop) {
                forced = stop == 2;
                if (forced) {
                    if (job) {
                        TerminateJobObject(job, 1);
                    } else {
                        TerminateProcess(process, 1);
                    }
                }
                closedByRequest = true;
                break;
            }
            if (resize) {
                if (console) {
                    const HRESULT result = resizeConsole(
                        console, {static_cast<SHORT>(size.width()), static_cast<SHORT>(size.height())});
                    if (FAILED(result)) {
                        reportError(QPtyProcess::ResizeError,
                                    windowsError("ResizePseudoConsole", static_cast<DWORD>(result)));
                    }
                } else {
                    winpty_error_ptr_t error = nullptr;
                    if (!winpty_set_size(legacy, size.width(), size.height(), &error)) {
                        reportError(QPtyProcess::ResizeError, takeWinPtyError(error, "winpty_set_size failed"));
                    }
                }
            }
        }
        DWORD code = 0;
        if (!closedByRequest) {
            GetExitCodeProcess(process, &code);
        }
        // 保留一个进程句柄，以便关闭控制台后仍能读取最终退出码。
        HANDLE exitedProcess = nullptr;
        DuplicateHandle(GetCurrentProcess(), process, GetCurrentProcess(), &exitedProcess, 0, FALSE,
                        DUPLICATE_SAME_ACCESS);
        cleanup(!closedByRequest && !forced);
        if (exitedProcess) {
            if (WaitForSingleObject(exitedProcess, 5000) == WAIT_TIMEOUT) {
                TerminateProcess(exitedProcess, 1);
                WaitForSingleObject(exitedProcess, INFINITE);
            }
            GetExitCodeProcess(exitedProcess, &code);
            closeHandle(exitedProcess);
        }
        finished(static_cast<int>(code),
                 forced || code >= 0xC0000000u ? QPtyProcess::CrashExit : QPtyProcess::NormalExit);
    }
} // namespace

std::unique_ptr<QPtyProcessBackend> createPtyBackend(QPtyProcessPrivate *owner, QPtyProcessOptions options) {
    return std::make_unique<WindowsPtyBackend>(owner, std::move(options));
}
