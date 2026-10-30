#pragma once

#include <QByteArray>
#include <QObject>
#include <QProcessEnvironment>
#include <QScopedPointer>
#include <QSize>
#include <QStringList>

/** 保存进程配置、缓冲区与平台后端，避免公共接口依赖系统头文件。 */
class QPtyProcessPrivate;

/**
 * 异步伪终端进程。Unix 使用原生 PTY，Windows 自动选择 ConPTY 或 winpty。
 * 每个对象只允许启动一次，退出、终止或启动失败后永久进入 Stopped。
 * 所有公共接口均在对象所属线程调用，该线程必须运行 Qt 事件循环。
 * 标准输出和错误输出合并为一个终端字节流；本类不解析 VT，也不进行字符编码转换。
 */
class QPtyProcess : public QObject {
    Q_OBJECT
public:
    /** 进程生命周期；Stopped 为终态，后续 start() 不再执行。 */
    enum ProcessState {
        NotRunning, ///< 初始状态，尚未尝试启动或请求停止。
        Starting,   ///< 正在创建终端并启动程序。
        Running,    ///< 程序已成功启动。
        Stopped     ///< 启动失败、退出或终止，资源已回收，不允许重新启动。
    };
    Q_ENUM(ProcessState)

    /** 区分正常退出和信号／强制终止。 */
    enum ExitStatus {
        NormalExit, ///< 程序自行退出，退出码可以非零。
        CrashExit   ///< Unix 信号终止、Windows 异常或 kill() 强制结束。
    };
    Q_ENUM(ExitStatus)

    /** 异步操作失败的类别；详细原因通过 errorString() 获取。 */
    enum ProcessError {
        NoError,            ///< 当前会话尚未报告错误。
        FailedToStart,      ///< 创建 PTY、查找程序或执行程序失败。
        ReadError,          ///< 读取终端失败。
        WriteError,         ///< 写入失败、进程未运行或发送缓冲区已满。
        ResizeError,        ///< 行列数无效或原生调整尺寸失败。
        UnsupportedBackend, ///< 当前平台不提供指定的后端。
        UnknownError        ///< 其他系统错误。
    };
    Q_ENUM(ProcessError)

    /** 可自动选择，也可强制后端以验证兼容性。 */
    enum Backend {
        AutoBackend, ///< Windows 优先 ConPTY；API 不存在时选择 winpty。
        UnixPty,     ///< Unix 原生伪终端。
        ConPty,      ///< Windows 10 1809+ 的原生伪控制台。
        WinPty       ///< Windows 7+ 的 winpty 兼容后端，需要 winpty-agent.exe。
    };
    Q_ENUM(Backend)

    /** 创建未启动的进程；parent 管理 QObject 生命周期。 */
    explicit QPtyProcess(QObject *parent = nullptr);
    /** 终止仍在运行的会话并等待后台线程回收，不遗留宿主线程或僵尸进程。 */
    ~QPtyProcess() override;

    /** 设置启动的 program，可为完整路径或按子进程 PATH 查找的名称；不隐式调用 shell。 */
    void setProgram(const QString &program);
    /** 返回启动的程序。 */
    QString program() const;
    /** 设置启动的 arguments；每项为独立参数，不需要调用方添加引号。 */
    void setArguments(const QStringList &arguments);
    /** 返回启动的参数。 */
    QStringList arguments() const;
    /** 设置启动的 directory；空字符串表示启动时继承宿主当前目录。 */
    void setWorkingDirectory(const QString &directory);
    /** 返回配置的工作目录。 */
    QString workingDirectory() const;
    /** 用 environment 完整替换启动环境；未调用时在 start() 时继承宿主环境。 */
    void setProcessEnvironment(const QProcessEnvironment &environment);
    /** 返回配置的环境，未配置时返回宿主当前环境。 */
    QProcessEnvironment processEnvironment() const;
    /** 设置启动的 backend；AutoBackend 根据运行时能力选择，失败不会偷偷切换后端。 */
    void setBackend(Backend backend);
    /** 返回后端选择策略。 */
    Backend backend() const;
    /** 返回本次会话的实际后端，尚未成功启动时为 AutoBackend。 */
    Backend activeBackend() const;
    /** 返回当前状态。 */
    ProcessState state() const;
    /** 返回运行中子进程 ID；没有运行中的进程时为零。 */
    qint64 processId() const;
    /** 返回本次会话的退出码；Unix 信号终止时返回 128 + 信号编号。 */
    int exitCode() const;
    /** 返回本次会话的退出类别。 */
    ExitStatus exitStatus() const;
    /** 返回最近一次错误类别；start() 清除旧错误。 */
    ProcessError error() const;
    /** 返回最近一次错误的描述。 */
    QString errorString() const;
    /** 返回期望字符网格尺寸，width 为列数、height 为行数，默认 80×24。 */
    QSize terminalSize() const;
    /** 返回已接收但尚未 readAll() 的字节数。 */
    qint64 bytesAvailable() const;
    /** 返回已接受但尚未确认写入操作系统的字节数。 */
    qint64 bytesToWrite() const;
    /** 取出并清空接收缓冲区；finished() 后仍可读取最后的输出。 */
    QByteArray readAll();

public Q_SLOTS:
    /** 按配置异步启动；仅 NotRunning 状态允许调用，后续调用均忽略，失败发出 errorOccurred 而不发 finished。 */
    void start();
    /** 将 data 原样加入发送队列，返回接受的字节数；失败返回 -1，单会话待发送上限为 4 MiB。 */
    qint64 write(const QByteArray &data);
    /** 设置 size 字符网格，范围为每维 1..32767；活动会话异步调整，失败通过 errorOccurred 报告。 */
    bool setTerminalSize(const QSize &size);
    /** 请求退出，未启动时直接进入 Stopped；Unix 向前台和 shell 进程组发送 SIGTERM；Windows 关闭控制台会话。 */
    void terminate();
    /** 强制结束会话，未启动时直接进入 Stopped；Unix 使用 SIGKILL；Windows 终止子进程并关闭控制台。 */
    void kill();

Q_SIGNALS:
    /** 子进程启动成功，此时 processId() 和 activeBackend() 可用。 */
    void started();
    /** 接收缓冲区有新增字节，使用 readAll() 取出。 */
    void readyRead();
    /** count 字节已写入原生终端输入通道，不表示子进程已经处理。 */
    void bytesWritten(qint64 count);
    /** 子进程退出且输出排空、资源回收完毕；code/status 为此次会话的退出结果。 */
    void finished(int code, QPtyProcess::ExitStatus status);
    /** 生命周期变为 state。 */
    void stateChanged(QPtyProcess::ProcessState state);
    /** 操作发生 error，errorString() 保存对应说明。 */
    void errorOccurred(QPtyProcess::ProcessError error);

private:
    QScopedPointer<QPtyProcessPrivate> d_ptr; ///< 独占配置、缓冲区和原生进程后端。
    Q_DECLARE_PRIVATE(QPtyProcess)
    Q_DISABLE_COPY(QPtyProcess)
};
