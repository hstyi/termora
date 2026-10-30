#include "qptyprocess_p.h"

#include <QDir>
#include <QFile>
#include <cerrno>
#include <climits>
#include <cstring>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <vector>
#ifdef __APPLE__
#include <util.h>
#elif defined(__FreeBSD__)
#include <libutil.h>
#else
#include <pty.h>
#endif
#ifdef __linux__
#include <sys/syscall.h>
#endif

namespace {
    /** 组合 operation 和 code 对应的系统错误。 */
    QString unixError(const char *operation, int code) {
        return QString::fromLatin1(operation) + QStringLiteral(": ") + QString::fromLocal8Bit(strerror(code));
    }

    /** 关闭 fd 并置为 -1；close 被信号打断时不重试，以免关闭已被其他线程复用的描述符。 */
    void closeFd(int &fd) {
        if (fd >= 0) {
            ::close(fd);
        }
        fd = -1;
    }

    /** 将 fd 移出标准流范围并设置 close-on-exec，成功时返回新描述符。 */
    int privateFd(int fd) {
        if (fd < 0) {
            return -1;
        }
        const int copy = fcntl(fd, F_DUPFD_CLOEXEC, 3);
        const int code = errno;
        ::close(fd);
        errno = code;
        return copy;
    }

    /** 创建 fds 管道的独占描述符；nonblocking 决定是否设置 O_NONBLOCK。 */
    bool createPipe(int (&fds)[2], bool nonblocking) {
        if (::pipe(fds) < 0) {
            return false;
        }
        fds[0] = privateFd(fds[0]);
        fds[1] = privateFd(fds[1]);
        if (fds[0] < 0 || fds[1] < 0) {
            const int code = errno;
            closeFd(fds[0]);
            closeFd(fds[1]);
            errno = code;
            return false;
        }
        if (nonblocking && (fcntl(fds[0], F_SETFL, O_NONBLOCK) < 0 || fcntl(fds[1], F_SETFL, O_NONBLOCK) < 0)) {
            const int code = errno;
            closeFd(fds[0]);
            closeFd(fds[1]);
            errno = code;
            return false;
        }
        return true;
    }

    /** 设置新终端的交互默认值，支持回显、作业控制、Ctrl+C 和 UTF-8 输入。 */
    termios terminalAttributes() {
        termios value = {};
        value.c_iflag = ICRNL | IXON;
#ifdef IUTF8
        value.c_iflag |= IUTF8;
#endif
        value.c_oflag = OPOST | ONLCR;
        value.c_cflag = CS8 | CREAD | HUPCL;
        value.c_lflag = ISIG | ICANON | ECHO | ECHOE | ECHOK | IEXTEN;
        value.c_cc[VINTR] = 3;
        value.c_cc[VQUIT] = 28;
        value.c_cc[VERASE] = 127;
        value.c_cc[VKILL] = 21;
        value.c_cc[VEOF] = 4;
        value.c_cc[VSTART] = 17;
        value.c_cc[VSTOP] = 19;
        value.c_cc[VSUSP] = 26;
        value.c_cc[VMIN] = 1;
#ifdef VWERASE
        value.c_cc[VWERASE] = 23;
#endif
#ifdef VLNEXT
        value.c_cc[VLNEXT] = 22;
#endif
        cfsetispeed(&value, B38400);
        cfsetospeed(&value, B38400);
        return value;
    }

    /** 原生 PTY 后端：一个 poll 线程串行处理输入、尺寸、信号和 waitpid。 */
    class UnixPtyBackend final : public QPtyProcessBackend {
    public:
        /** 使用公共构造参数 owner/options 初始化会话。 */
        UnixPtyBackend(QPtyProcessPrivate *owner, QPtyProcessOptions options)
            : QPtyProcessBackend(owner, std::move(options)) {}

        /** 先回收子进程和线程，再关闭唤醒管道。 */
        ~UnixPtyBackend() override {
            shutdown();
            closeFd(wakePipe[0]);
            closeFd(wakePipe[1]);
        }

        /** 建立控制管道并启动事件循环。 */
        void launch() override {
            if (!createPipe(wakePipe, true)) {
                failed(unixError("pipe", errno));
                return;
            }
            worker = std::thread([this] { run(); });
        }

        /** 请求强制终止并等待事件循环返回。 */
        void shutdown() override {
            stop(true);
            if (worker.joinable()) {
                worker.join();
            }
        }

    private:
        /** 通知 poll 检查共享命令；管道满说明已有待处理唤醒，无需再写。 */
        void wake() override {
            if (wakePipe[1] >= 0) {
                const char byte = 1;
                ssize_t count;
                do {
                    count = ::write(wakePipe[1], &byte, 1);
                } while (count < 0 && errno == EINTR);
            }
        }

        /** 创建终端及子进程，然后排空输出并回收进程。 */
        void run();

        /** 向 pid 对应的 shell 组及 master 指向的前台组发送 signal，避免影响宿主进程组。 */
        void signalSession(pid_t pid, int master, int signal) {
            const pid_t foreground = tcgetpgrp(master);
            if (foreground > 0 && foreground != pid && foreground != getpgrp()) {
                ::kill(-foreground, signal);
            }
            ::kill(-pid, signal);
            ::kill(pid, signal);
        }

        int wakePipe[2] = {-1, -1}; ///< 后台控制管道，工作线程退出前始终保持两端打开。
        std::thread worker;         ///< 唯一访问主 PTY 和子进程状态的线程。
    };

    void UnixPtyBackend::run() {
        if (options.backend != QPtyProcess::AutoBackend && options.backend != QPtyProcess::UnixPty) {
            failed(QStringLiteral("Requested PTY backend is not available on Unix"), QPtyProcess::UnsupportedBackend);
            return;
        }
        // fork 之后仅调用异步信号安全的系统函数，所有 Qt 容器和字符串在父进程中准备。
        std::vector<QByteArray> arguments = {QFile::encodeName(options.program)};
        for (const auto &argument : options.arguments) {
            arguments.push_back(argument.toUtf8());
        }
        std::vector<char *> argv;
        for (auto &argument : arguments) {
            argv.push_back(argument.data());
        }
        argv.push_back(nullptr);
        std::vector<QByteArray> environment;
        for (const auto &entry : options.environment.toStringList()) {
            environment.push_back(entry.toUtf8());
        }
        std::vector<char *> envp;
        for (auto &entry : environment) {
            envp.push_back(entry.data());
        }
        envp.push_back(nullptr);
        std::vector<QByteArray> candidates;
        if (options.program.contains(QLatin1Char('/'))) {
            candidates.push_back(QFile::encodeName(QDir(options.directory).absoluteFilePath(options.program)));
        } else {
            const auto path = options.environment.value(QStringLiteral("PATH"), QStringLiteral("/usr/bin:/bin"));
            for (const auto &part : path.split(QLatin1Char(':'))) {
                candidates.push_back(
                    QFile::encodeName(QDir(QDir(options.directory).absoluteFilePath(part)).filePath(options.program)));
            }
        }
        std::vector<const char *> executables;
        for (const auto &candidate : candidates) {
            executables.push_back(candidate.constData());
        }
        const QByteArray directory = QFile::encodeName(options.directory);
        const char *cwd = directory.constData();
        char **argData = argv.data();
        char **envData = envp.data();
        const char *const *execData = executables.data();
        const size_t execCount = executables.size();
        rlimit limit = {};
        if (getrlimit(RLIMIT_NOFILE, &limit) < 0) {
            limit.rlim_cur = 65536;
        }
        const int maxFd = limit.rlim_cur == RLIM_INFINITY ? 65536
                                                          : static_cast<int>(std::min<rlim_t>(limit.rlim_cur, INT_MAX));
        struct sigaction defaultAction = {};
        defaultAction.sa_handler = SIG_DFL;
        sigemptyset(&defaultAction.sa_mask);
        sigset_t emptyMask;
        sigemptyset(&emptyMask);
        int statusPipe[2] = {-1, -1};
        if (!createPipe(statusPipe, false)) {
            failed(unixError("startup pipe", errno));
            return;
        }
        termios attributes = terminalAttributes();
        winsize size = {};
        size.ws_col = options.size.width();
        size.ws_row = options.size.height();
        int master = -1;
        const pid_t pid = forkpty(&master, nullptr, &attributes, &size);
        if (pid == 0) {
            // 描述符 3 专用于报告 exec/chdir 错误，成功 exec 时由 CLOEXEC 自动关闭。
            int code = 0;
            if (statusPipe[1] != 3 && dup2(statusPipe[1], 3) < 0) {
                _exit(127);
            }
            fcntl(3, F_SETFD, FD_CLOEXEC);
#ifdef SYS_close_range
            if (syscall(SYS_close_range, 4u, ~0u, 0u) < 0) {
#endif
                for (int fd = 4; fd < maxFd; ++fd) {
                    ::close(fd);
                }
#ifdef SYS_close_range
            }
#endif
            for (int signal = 1; signal < NSIG; ++signal) {
                if (signal != SIGKILL && signal != SIGSTOP) {
                    sigaction(signal, &defaultAction, nullptr);
                }
            }
            sigprocmask(SIG_SETMASK, &emptyMask, nullptr);
            if (chdir(cwd) < 0) {
                code = errno;
            } else {
                code = ENOENT;
                for (size_t i = 0; i < execCount; ++i) {
                    execve(execData[i], argData, envData);
                    if (errno != ENOENT && errno != ENOTDIR) {
                        code = errno;
                        if (errno != EACCES) {
                            break;
                        }
                    }
                }
            }
            const char *bytes = reinterpret_cast<const char *>(&code);
            size_t left = sizeof(code);
            while (left) {
                const ssize_t count = ::write(3, bytes, left);
                if (count > 0) {
                    bytes += count;
                    left -= count;
                } else if (errno != EINTR) {
                    break;
                }
            }
            _exit(127);
        }
        const int forkError = errno;
        closeFd(statusPipe[1]);
        if (pid < 0) {
            closeFd(statusPipe[0]);
            closeFd(master);
            failed(unixError("forkpty", forkError));
            return;
        }
        int execError = 0;
        size_t readBytes = 0;
        while (readBytes < sizeof(execError)) {
            const ssize_t count = ::read(statusPipe[0], reinterpret_cast<char *>(&execError) + readBytes,
                                         sizeof(execError) - readBytes);
            if (count > 0) {
                readBytes += count;
            } else if (count == 0) {
                break;
            } else if (errno != EINTR) {
                execError = errno;
                readBytes = sizeof(execError);
                break;
            }
        }
        closeFd(statusPipe[0]);
        if (readBytes || fcntl(master, F_SETFD, FD_CLOEXEC) < 0 || fcntl(master, F_SETFL, O_NONBLOCK) < 0) {
            const int code = readBytes ? execError : errno;
            signalSession(pid, master, SIGKILL);
            closeFd(master);
            while (waitpid(pid, nullptr, 0) < 0 && errno == EINTR) {}
            failed(unixError("Start PTY process", code));
            return;
        }
        started(pid, QPtyProcess::UnixPty);
        QByteArray input;
        int offset = 0, status = 0, sentStop = 0;
        bool reaped = false, eof = false;
        while (true) {
            int stop;
            bool resize;
            QSize nextSize;
            {
                std::lock_guard<std::mutex> lock = std::lock_guard<std::mutex>(mutex);
                stop = stopRequest;
                resize = std::exchange(resizePending, false);
                nextSize = requestedSize;
                if (offset == input.size()) {
                    input = std::exchange(pendingInput, {});
                    offset = 0;
                }
            }
            if (!reaped && stop > sentStop) {
                signalSession(pid, master, stop == 2 ? SIGKILL : SIGTERM);
                sentStop = stop;
            }
            if (resize && !reaped && !eof) {
                size.ws_col = nextSize.width();
                size.ws_row = nextSize.height();
                if (ioctl(master, TIOCSWINSZ, &size) < 0) {
                    reportError(QPtyProcess::ResizeError, unixError("TIOCSWINSZ", errno));
                }
            }
            if (!reaped) {
                pid_t result;
                do {
                    result = waitpid(pid, &status, WNOHANG);
                } while (result < 0 && errno == EINTR);
                reaped = result == pid || result < 0;
                if (result < 0) {
                    reportError(QPtyProcess::UnknownError, unixError("waitpid", errno));
                    status = 0;
                }
            }
            // 每轮限制读取量，让大量输出不会饿死 kill/resize 等控制命令。
            int total = 0;
            while (!eof && total < 256 * 1024) {
                char buffer[16384];
                const ssize_t count = ::read(master, buffer, sizeof(buffer));
                if (count > 0) {
                    output(QByteArray(buffer, static_cast<int>(count)));
                    total += count;
                } else if (count == 0 || (count < 0 && errno == EIO)) {
                    eof = true;
                } else if (errno == EINTR) {
                    continue;
                } else if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    break;
                } else {
                    reportError(QPtyProcess::ReadError, unixError("read PTY", errno));
                    eof = true;
                }
            }
            // 根进程退出后读取有限尾部，不让继续输出的后台孙进程拖住 finished。
            if (reaped) {
                break;
            }
            if (!eof && offset < input.size()) {
                const ssize_t count = ::write(master, input.constData() + offset, input.size() - offset);
                if (count > 0) {
                    offset += count;
                    written(count);
                } else if (count < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR) {
                    reportError(QPtyProcess::WriteError, unixError("write PTY", errno));
                    signalSession(pid, master, SIGKILL);
                }
            }
            pollfd descriptors[2] = {
                {wakePipe[0], POLLIN, 0},
                {eof ? -1 : master, static_cast<short>(POLLIN | (offset < input.size() ? POLLOUT : 0)), 0}};
            // 短超时用于 waitpid，不接管宿主应用的 SIGCHLD 处理器。
            const int result = ::poll(descriptors, 2, 30);
            if (result < 0 && errno != EINTR) {
                reportError(QPtyProcess::ReadError, unixError("poll PTY", errno));
                signalSession(pid, master, SIGKILL);
            }
            char discarded[128];
            while (::read(wakePipe[0], discarded, sizeof(discarded)) > 0) {}
        }
        closeFd(master);
        const auto exitStatus = WIFEXITED(status) ? QPtyProcess::NormalExit : QPtyProcess::CrashExit;
        const int exitCode = WIFEXITED(status) ? WEXITSTATUS(status)
                                               : (WIFSIGNALED(status) ? 128 + WTERMSIG(status) : -1);
        finished(exitCode, exitStatus);
    }
} // namespace

std::unique_ptr<QPtyProcessBackend> createPtyBackend(QPtyProcessPrivate *owner, QPtyProcessOptions options) {
    return std::make_unique<UnixPtyBackend>(owner, std::move(options));
}
