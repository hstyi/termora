#include <QApplication>
#include <QDebug>
#include <QDir>
#include <QPushButton>
#include <QVBoxLayout>
#include <qpty/qptyprocess.h>
#include <qssh/qsshclient.h>
#include <qtermwidget/qtermwidget.h>

int main(int argc, char *argv[]) {
    auto app = QApplication(argc, argv); ///< 管理 GUI 事件循环。
    auto *widget = new QWidget;
    auto *layout = new QVBoxLayout;
    widget->setLayout(layout);
    new QSshClient();
    auto terminal = QTermWidget(); ///< 展示 shell 输出并编码终端输入。
    // 后声明进程，使退出事件循环后先回收 shell，再释放终端视图。
    auto process = QPtyProcess(); ///< 当前窗口唯一的本地 PTY 会话。

#ifdef Q_OS_WIN
    auto shell = qEnvironmentVariable("COMSPEC"); ///< Windows 配置的命令解释器。
    if (shell.isEmpty()) {
        shell = QStringLiteral("cmd.exe");
    }
#else
    auto shell = qEnvironmentVariable("SHELL"); ///< 当前用户配置的 Unix shell。
    if (shell.isEmpty()) {
        shell = QStringLiteral("/bin/sh");
    }
    process.setArguments({QStringLiteral("-i")});
#endif
    process.setProgram(shell);
    process.setWorkingDirectory(QDir::homePath());

    // data 包括键盘、粘贴和 Ghostty 的协议应答；会话结束后不再发送。
    QObject::connect(&terminal, &QTermWidget::dataToSend, &process, [&process](const QByteArray &data) {
        if (process.state() == QPtyProcess::Running) {
            process.write(data);
        }
    });
    QObject::connect(&process, &QPtyProcess::readyRead, &terminal,
                     [&terminal, &process] { terminal.write(process.readAll()); });
    // 信号携带字符列数和行数，PTY 不接收窗口的像素尺寸。
    QObject::connect(&terminal, &QTermWidget::terminalSizeChanged, &process, &QPtyProcess::setTerminalSize);
    QObject::connect(&terminal, &QTermWidget::titleChanged, &terminal, &QWidget::setWindowTitle);
    QObject::connect(&process, &QPtyProcess::started, &terminal, [&terminal] {
        terminal.setEnabled(true);
        terminal.setFocus();
    });
    // shell 退出时关闭窗口；关闭窗口退出应用时由 process 析构回收仍存活的 shell。
    QObject::connect(&process, &QPtyProcess::finished, &terminal, &QWidget::close);
    // 失败原因留在终端中，启动失败时也能查看，而不会得到一个空白窗口。
    QObject::connect(&process, &QPtyProcess::errorOccurred, &terminal, [&terminal, &process] {
        qWarning().noquote() << process.errorString();
        terminal.write(QStringLiteral("\r\n[PTY] %1\r\n").arg(process.errorString()).toUtf8());
    });

    auto *buttons_layout = new QHBoxLayout();
    buttons_layout->setSpacing(4);
    buttons_layout->setContentsMargins(0, 0, 0, 0);
    auto *font_incr_button = new QPushButton("Font+");
    auto *font_decr_button = new QPushButton("Font-");
    buttons_layout->addWidget(font_decr_button);
    buttons_layout->addWidget(font_incr_button);

    QObject::connect(font_incr_button, &QPushButton::clicked, [&] {
        auto font = terminal.font();
        font.setPointSizeF(font.pointSizeF() + 1);
        terminal.setFont(font);
    });

    QObject::connect(font_decr_button, &QPushButton::clicked, [&] {
        auto font = terminal.font();
        font.setPointSizeF(font.pointSizeF() - 1);
        terminal.setFont(font);
    });

    layout->setSpacing(0);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->addWidget(&terminal, 1);
    layout->addLayout(buttons_layout, 1);

    widget->setWindowTitle(QStringLiteral("Termora"));
    widget->resize(500, 500);
    widget->show();

    // show() 完成首次布局后同步初始字符尺寸，避免 shell 以默认 80×24 启动。
    process.setTerminalSize(terminal.terminalSize());
    process.start();
    return QApplication::exec();
}
