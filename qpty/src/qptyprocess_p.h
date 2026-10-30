#pragma once

#include <atomic>
#include <condition_variable>
#include <functional>
#include <memory>
#include <mutex>
#include <qpty/qptyprocess.h>
#include <thread>

/** start() 时拍摄的配置快照，后台线程不读取 QObject 的可变属性。 */
struct QPtyProcessOptions {
    QString program;                 ///< 待执行程序，不含 shell 命令拼接。
    QStringList arguments;           ///< 原始参数列表。
    QString directory;               ///< 已转为绝对路径的工作目录。
    QProcessEnvironment environment; ///< 子进程完整环境。
    QSize size;                      ///< 初始终端字符尺寸。
    QPtyProcess::Backend backend;    ///< 用户选定的后端策略。
};

/** 平台无关的后台会话；子类在析构前必须停止并 join 工作线程。 */
class QPtyProcessBackend {
public:
    /** 将 options 配置与 owner 所属线程的通知目标绑定。 */
    QPtyProcessBackend(QPtyProcessPrivate *owner, QPtyProcessOptions options);
    /** 子类已停止原生 I/O 后释放公共队列。 */
    virtual ~QPtyProcessBackend() = default;
    /** 启动后台会话线程，返回后可立即接收控制命令。 */
    virtual void launch() = 0;
    /** 停止并回收会话；可重复调用，析构中必须调用。 */
    virtual void shutdown() = 0;
    /** 将 data 放入后台发送队列。 */
    void enqueue(const QByteArray &data);
    /** 请求将字符网格变为 size。 */
    void resize(const QSize &size);
    /** 请求终止；force 为 true 时升级为强制结束，不能被后续温和请求降级。 */
    void stop(bool force);

protected:
    /** 唤醒后端事件循环，调用方可能是 QObject 线程或析构线程。 */
    virtual void wake() = 0;
    /** 转发成功启动的 pid 与实际 backend，通知在 QObject 线程执行。 */
    void started(qint64 pid, QPtyProcess::Backend backend);
    /** 转发新收到的 data，通知在 QObject 线程执行。 */
    void output(const QByteArray &data);
    /** 转发已写出的 count 字节。 */
    void written(qint64 count);
    /** 转发 error 与 message；不会自动改变进程状态。 */
    void reportError(QPtyProcess::ProcessError error, const QString &message);
    /** 报告启动失败的 message；error 默认表示原生启动失败。 */
    void failed(const QString &message, QPtyProcess::ProcessError error = QPtyProcess::FailedToStart);
    /** 原生资源已释放，报告 code/status 退出结果。 */
    void finished(int code, QPtyProcess::ExitStatus status);

    QPtyProcessPrivate *owner;          ///< 析构会等待本后端退出，因此回调期间该对象始终有效。
    QPtyProcessOptions options;         ///< 启动配置快照，仅后台线程访问。
    std::mutex mutex;                   ///< 保护输入队列和待处理的控制请求。
    std::condition_variable inputReady; ///< 唤醒 Windows 的独立输入线程。
    QByteArray pendingInput;            ///< 尚未交给 I/O 循环的数据。
    QSize requestedSize;                ///< 最新一次调整尺寸请求。
    bool resizePending = false;         ///< 是否需要提交 requestedSize。
    int stopRequest = 0;                ///< 0 无请求、1 温和结束、2 强制结束。
};

/** 创建当前编译平台的后端；owner 为通知目标，options 为独立配置副本。 */
std::unique_ptr<QPtyProcessBackend> createPtyBackend(QPtyProcessPrivate *owner, QPtyProcessOptions options);

/** 保存 QObject 线程中的状态，并把后台通知排队送回该线程。 */
class QPtyProcessPrivate {
public:
    /** 绑定 process 所指的进程对象，所有公开状态都由其所属线程维护。 */
    explicit QPtyProcessPrivate(QPtyProcess *process);
    /** 停止后台会话后再释放回调目标。 */
    ~QPtyProcessPrivate();
    /** 将 task 排入对象事件队列；对象销毁时 Qt 自动移除未处理的通知。 */
    void post(std::function<void()> task);
    /** 更新 error/message 并通知使用方。 */
    void reportError(QPtyProcess::ProcessError error, const QString &message);

    QPtyProcess *q;                                               ///< 对外的 QObject，不拥有该指针。
    QString program;                                              ///< 启动的程序。
    QStringList arguments;                                        ///< 启动的参数。
    QString directory;                                            ///< 启动的工作目录，空值表示继承。
    QProcessEnvironment environment;                              ///< 用户设置的完整子进程环境。
    bool customEnvironment = false;                               ///< 是否覆盖启动时的宿主环境。
    QSize size = {80, 24};                                        ///< 最近请求的字符网格尺寸。
    QPtyProcess::Backend preference = QPtyProcess::AutoBackend;   ///< 启动的后端策略。
    QPtyProcess::Backend active = QPtyProcess::AutoBackend;       ///< 成功启动的后端。
    QPtyProcess::ProcessState state = QPtyProcess::NotRunning;    ///< 当前会话状态。
    QPtyProcess::ProcessError error = QPtyProcess::NoError;       ///< 最近一次错误类别。
    QString errorText;                                            ///< 最近一次错误描述。
    qint64 pid = 0;                                               ///< 运行中的原生子进程 ID。
    int exitCode = 0;                                             ///< 本次会话的退出码。
    QPtyProcess::ExitStatus exitStatus = QPtyProcess::NormalExit; ///< 本次会话的退出类别。
    QByteArray received;                                          ///< 保留尚未被 readAll() 取走的原始字节。
    qint64 queuedBytes = 0;                                       ///< 已接受但尚未收到写入确认的字节数。
    std::unique_ptr<QPtyProcessBackend> backend; ///< 最后声明以最先析构，停止 I/O 后才释放回调所访问的其他状态。
};
