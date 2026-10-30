#pragma once

#include <QSize>
#include <asio.hpp>
#include <functional>
#include <libssh/callbacks.h>
#include <libssh/libssh.h>
#include <memory>
#include <mutex>
#include <qssh/qsshchannel.h>

/** 所有 Channel 共用的 Qt 私有实现，Shell/Exec 仅选择不同的打开阶段。 */
class QSshChannelPrivate {
public:
    /** 绑定 owner 与 session；shell 选择 PTY+shell 或 exec 请求。 */
    QSshChannelPrivate(QSshChannel *owner, QSshSession *session, bool shell);
    /** 取消时限并从 Session 移除和释放句柄。 */
    ~QSshChannelPrivate();
    /** 把 task 投递到 worker，并用生命周期标志屏蔽析构后的回调。 */
    void submit(std::function<void()> task) const;
    /** 把 task 排队到所属 Qt 线程。 */
    void notify(std::function<void()> task) const;
    /** 设置后台状态 value，并排队更新前端快照。 */
    void setState(QSshChannel::State value);
    /** 使用 argument 打开 Channel，含义为命令或终端类型。 */
    void open(const QString &argument);
    /** 推进一步打开／读写／关闭，返回是否有实际进展。 */
    bool advance();
    /** 把 stderrStream 对应的远端缓冲搬入有界接收缓冲，返回是否读到数据。 */
    bool receive(bool stderrStream);
    /** 合并数据和写入进度通知，避免大量输出挤满 Qt 事件队列。 */
    void notifyData();
    /** 设置打开或关闭超时，超时后释放 Channel。 */
    void setDeadline();
    /** 报告 error/message；fatal 为 true 时同时关闭本 Channel。 */
    void fail(QSshChannel::Error error, const QString &message, bool fatal = true);
    /** 释放句柄并报告终态；Session 断开及 abort() 会丢弃尚在 libssh 的输出。 */
    void finish();
    /** 返回并清空 stderrStream 对应的接收缓冲，释放容量后唤醒 Session。 */
    QByteArray take(bool stderrStream);

    QSshChannel *q;                                       ///< 前端 Channel 对象。
    QSshSessionPrivate *session;                          ///< 所属 Session，晚于本 Channel 销毁。
    const bool shell;                                     ///< 是否使用 PTY+shell 打开流程。
    QSshChannel::State frontState = QSshChannel::Idle;    ///< Qt 线程独占的状态快照。
    QSshChannel::Error frontError = QSshChannel::NoError; ///< Qt 线程独占的错误类别。
    QString frontErrorText;                               ///< Qt 线程独占的错误说明。
    bool frontClosing = false;                            ///< Qt 线程独占，抑制关闭后的迟到 opened 通知。
    bool frontEof = false;                                ///< Qt 线程独占，拒绝 EOF 请求之后的新输入。
    QSize frontSize = {80, 24};                           ///< Qt 线程独占的期望字符网格。
    int frontExitCode = -1;                               ///< Qt 线程独占，远端退出码快照。
    bool frontHasExitCode = false;                        ///< Qt 线程独占，退出码是否由远端提供。
    QString frontExitSignal;                              ///< Qt 线程独占的退出信号快照。

    mutable std::mutex bufferMutex;   ///< 仅保护下列收发缓冲和合并通知字段，不保护协议句柄。
    QByteArray stdoutBuffer;          ///< 跨线程有界 stdout 缓冲。
    QByteArray stderrBuffer;          ///< 跨线程有界 stderr 缓冲。
    QByteArray inputBuffer;           ///< 跨线程有界待发送字节。
    bool acceptingInput = false;      ///< 后台仍接受输入；防止前端关闭通知迟到时追加到已关闭队列。
    qint64 writtenPending = 0;        ///< 等待一次合并通知的 libssh 接受字节数。
    bool stdoutPending = false;       ///< 合并 stdout 就绪通知。
    bool stderrPending = false;       ///< 合并 stderr 就绪通知。
    bool notificationPending = false; ///< 已有一条缓冲通知排队到 Qt。

    std::shared_ptr<bool> alive = std::make_shared<bool>(true); ///< worker 独占的存活标志。
    asio::steady_timer deadline;                                ///< Channel 打开或主动关闭的时限。
    ssh_channel handle = nullptr;                               ///< worker 独占的 libssh Channel。
    ssh_channel_callbacks_struct callbacks = {};                ///< 生命周期覆盖句柄的退出信息回调。
    QSshChannel::State phase = QSshChannel::Idle;               ///< worker 独占的阶段。
    QByteArray openArgument;                                    ///< worker 独占，命令或终端类型的 UTF-8 副本。
    int openStep = 0;               ///< 依次执行 channel-open、Agent、X11、PTY、shell/exec 请求。
    bool eofRequested = false;      ///< 等待排空输入后发送 EOF。
    bool eofSent = false;           ///< EOF 已交给 libssh。
    bool closeRequested = false;    ///< 等待排空输入并主动关闭。
    bool closeSent = false;         ///< 关闭报文已交给 libssh。
    bool eofNotified = false;       ///< 已向 Qt 通知远端 EOF。
    bool remoteClosed = false;      ///< 回调记录远端关闭，不在回调内释放句柄。
    bool hasCode = false;           ///< worker 已收到退出码。
    int code = -1;                  ///< worker 收到的退出码。
    QString signal;                 ///< worker 收到的退出信号。
    QSize requestedSize = {80, 24}; ///< worker 期望的最新终端尺寸。
    QSize resizingSize;             ///< 进行中的窗口请求尺寸，重试期间保持不变。
    QSize appliedSize;              ///< 最近一次完成的远端终端尺寸。
};
