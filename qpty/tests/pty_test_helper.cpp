#include <QByteArray>
#include <QCoreApplication>
#include <QDir>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#ifdef Q_OS_WIN
#include <io.h>
#include <windows.h>
#else
#include <fcntl.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <unistd.h>
#endif

namespace {
    /** 将 data 原样写入输出并刷新，避免 stdio 缓冲掩盖终端行为。 */
    void output(const QByteArray &data) {
        fwrite(data.constData(), 1, data.size(), stdout);
        fflush(stdout);
    }

    /** 返回当前字符尺寸，直接询问原生终端而非读取环境变量。 */
    QByteArray sizeReport() {
        int columns = 0, rows = 0;
#ifdef Q_OS_WIN
        CONSOLE_SCREEN_BUFFER_INFO info = {};
        if (GetConsoleScreenBufferInfo(GetStdHandle(STD_OUTPUT_HANDLE), &info)) {
            columns = info.srWindow.Right - info.srWindow.Left + 1;
            rows = info.srWindow.Bottom - info.srWindow.Top + 1;
        }
#else
        winsize size = {};
        if (ioctl(STDIN_FILENO, TIOCGWINSZ, &size) == 0) {
            columns = size.ws_col;
            rows = size.ws_row;
        }
#endif
        return "SIZE=" + QByteArray::number(columns) + "," + QByteArray::number(rows) + "\n";
    }

    /** 关闭回显；raw 为 true 时同时关闭行缓冲与控制字符解释。 */
    void configureInput(bool raw) {
#ifdef Q_OS_WIN
        DWORD mode = 0;
        const HANDLE handle = GetStdHandle(STD_INPUT_HANDLE);
        GetConsoleMode(handle, &mode);
        mode &= ~ENABLE_ECHO_INPUT;
        if (raw) {
            mode &= ~(ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT);
        }
        SetConsoleMode(handle, mode);
#else
        termios attributes = {};
        tcgetattr(STDIN_FILENO, &attributes);
        if (raw) {
            cfmakeraw(&attributes);
        }
        attributes.c_lflag &= ~ECHO;
        tcsetattr(STDIN_FILENO, TCSANOW, &attributes);
#endif
    }
} // namespace

/** 运行 argv 指定的验证模式，argc 为参数数量；退出码用于断言宿主的进程状态。 */
int main(int argc, char **argv) {
    QCoreApplication app = QCoreApplication(argc, argv);
#ifdef Q_OS_WIN
    SetConsoleCP(CP_UTF8);
    SetConsoleOutputCP(CP_UTF8);
#endif
    const auto arguments = app.arguments();
    const QString mode = arguments.value(1);
    if (mode == QStringLiteral("exit")) {
        output("FINAL-OUTPUT\n");
        return arguments.value(2).toInt();
    }
    if (mode == QStringLiteral("report")) {
#ifdef Q_OS_WIN
        const int a = _isatty(_fileno(stdin)), b = _isatty(_fileno(stdout)), c = _isatty(_fileno(stderr));
#else
        const int a = isatty(0), b = isatty(1), c = isatty(2);
#endif
        output("TTY=" + QByteArray::number(a != 0) + QByteArray::number(b != 0) + QByteArray::number(c != 0) + "\n");
        output(sizeReport());
        output("CWD=" + QDir::currentPath().toUtf8().toBase64() + "\n");
        output("ENV=" + qgetenv("QPTY_TEST_VALUE").toBase64() + "\n");
        for (int i = 2; i < arguments.size(); ++i) {
            output("ARG=" + arguments[i].toUtf8().toBase64() + "\n");
        }
        fputs("STDERR-MARKER\n", stderr);
        fflush(stderr);
        return 0;
    }
    if (mode == QStringLiteral("bulk")) {
        const QByteArray block = QByteArray(4096, 'Z');
        for (int i = 0; i < 256; ++i) {
            output(block);
        }
        output("\nBULK-END\n");
        return 0;
    }
    if (mode == QStringLiteral("receive")) {
        configureInput(true);
        output("READY\n");
        const int expected = arguments.value(2).toInt();
        int received = 0;
        char bytes[4096];
        while (received < expected) {
#ifdef Q_OS_WIN
            DWORD count = 0;
            if (!ReadFile(GetStdHandle(STD_INPUT_HANDLE), bytes, sizeof(bytes), &count, nullptr) || !count) {
                return 2;
            }
#else
            const ssize_t count = ::read(0, bytes, sizeof(bytes));
            if (count <= 0) {
                return 2;
            }
#endif
            for (int i = 0; i < static_cast<int>(count); ++i) {
                if (bytes[i] != 'x') {
                    return 3;
                }
            }
            received += count;
        }
        output("RECEIVED=" + QByteArray::number(received) + "\n");
        return 0;
    }
#ifndef Q_OS_WIN
    if (mode == QStringLiteral("fd")) {
        return fcntl(arguments.value(2).toInt(), F_GETFD) < 0 ? 0 : 4;
    }

#endif
    configureInput(false);
    output("READY\n");
    char line[4096];
    while (fgets(line, sizeof(line), stdin)) {
        QByteArray command = QByteArray(line);
        while (command.endsWith('\n') || command.endsWith('\r')) {
            command.chop(1);
        }
        if (command == "size") {
            output(sizeReport());
        } else if (command == "quit") {
            output("BYE\n");
            return 0;
        } else {
            output("ECHO=" + command.toBase64() + "\n");
        }
    }
    return 0;
}
