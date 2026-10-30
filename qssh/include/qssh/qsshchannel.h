#pragma once

#include <QByteArray>
#include <QObject>
#include <QScopedPointer>

class QSshSession;
class QSshChannelPrivate;
class QSshSessionPrivate;

/** Session 内的异步字节流；公共接口仅在所属 Qt 线程调用，不转换编码。 */
class QSshChannel : public QObject {
    Q_OBJECT
public:
    /** Closed 为终态，Channel 不可重复打开。 */
    enum State {
        Idle,    ///< 尚未请求打开。
        Opening, ///< 正在打开 Channel 或发送 PTY／shell／exec 请求。
        Open,    ///< 已打开，可以收发数据。
        Closing, ///< 正在排空输入并关闭。
        Closed   ///< 终态，不可再次打开。
    };
    Q_ENUM(State)

    /** Channel 级失败不主动断开其他 Channel。 */
    enum Error {
        NoError,      ///< 尚未发生错误。
        InvalidState, ///< 所属 Session 未就绪、参数无效或当前阶段不允许操作。
        OpenError,    ///< Channel、转发、PTY、shell 或 exec 请求失败。
        ReadError,    ///< 接收远端数据失败。
        WriteError,   ///< 写入、发送 EOF 或关闭请求失败。
        ResizeError,  ///< 远端终端尺寸调整失败。
        BufferFull,   ///< 写入会超过本地发送队列容量。
        TimeoutError, ///< 打开或主动关闭超过时限。
        SessionClosed ///< 所属 Session 关闭导致本 Channel 终止。
    };
    Q_ENUM(Error)

    /** 取消后台操作并释放 Channel，所属 Session 必须仍存活。 */
    ~QSshChannel() override;
    /** 返回前端已观察到的状态。 */
    State state() const;
    /** 返回最近一次失败类别。 */
    Error error() const;
    /** 返回最近一次失败说明。 */
    QString errorString() const;
    /** 取出并清空 stdout，finished() 后仍可读取尾部输出。 */
    QByteArray readAll();
    /** 取出并清空 stderr；PTY 输出通常已由服务器合并。 */
    QByteArray readAllStandardError();
    /** 返回 stdout 和 stderr 未读取的总字节数。 */
    qint64 bytesAvailable() const;
    /** 返回已接受但尚未交给 libssh 的发送字节数。 */
    qint64 bytesToWrite() const;
    /** 返回是否收到了远端退出码。 */
    bool hasExitCode() const;
    /** 返回远端退出码，未收到时返回 -1。 */
    int exitCode() const;
    /** 返回远端退出信号名称，未报告时为空。 */
    QString exitSignal() const;

public Q_SLOTS:
    /** 将 data 加入发送队列，返回接受字节数；非 Open、EOF 后或超过 4 MiB 上限时返回 -1。 */
    qint64 write(const QByteArray &data);
    /** 排空输入后发送 EOF，之后拒绝 write()，仍可接收输出。 */
    void sendEof();
    /** 排空输入后发送 EOF 和关闭请求，超时强制释放；可以重复调用。 */
    void close();
    /** 丢弃未发送输入并释放本 Channel，不断开所属 Session。 */
    void abort();

Q_SIGNALS:
    /** Channel 和 shell／exec 请求均已成功。 */
    void opened();
    /** stdout 有新增数据，用 readAll() 读取。 */
    void readyRead();
    /** stderr 有新增数据，用 readAllStandardError() 读取。 */
    void readyReadStandardError();
    /** count 字节已被 libssh 接受，不表示已送达或已被远端程序处理。 */
    void bytesWritten(qint64 count);
    /** 收到远端 EOF，不代表退出码或关闭消息已经到达。 */
    void readEof();
    /** 已关闭，可以读取退出信息和已接收数据；正常远端关闭先排空输出，abort／断线可能截断输出。 */
    void finished();
    /** 生命周期变为 state。 */
    void stateChanged(QSshChannel::State state);
    /** 发生 error，具体说明见 errorString()。 */
    void errorOccurred(QSshChannel::Error error);

protected:
    /** 创建 session 所属 Channel，shell 为 true 时使用 PTY 和 shell 请求。 */
    explicit QSshChannel(QSshSession *session, bool shell);
    QScopedPointer<QSshChannelPrivate> d_ptr; ///< libssh 句柄、缓冲和操作状态。
    Q_DECLARE_PRIVATE(QSshChannel)

private:
    Q_DISABLE_COPY(QSshChannel)
    friend class QSshSessionPrivate;
};

Q_DECLARE_METATYPE(QSshChannel::State)
Q_DECLARE_METATYPE(QSshChannel::Error)
