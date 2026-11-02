#pragma once

#include <QFileDevice>
#include <QIODevice>
#include <QObject>
#include <QScopedPointer>

class QSshSftpSession;
class QSshSftpTransferPrivate;

/**
 * 单次 SFTP 文件传输，由 SFTP 子系统拥有；公开方法及信号均在所属 Qt 线程使用。
 * 外部 QIODevice 保留原线程归属及所有权，所属 Qt 事件循环须运行到 finished；期间不得移动设备或并发读写。
 * 路径下载先写同目录临时文件再提交，普通本地文件 I/O 在唯一 worker 分块执行。
 */
class QSshSftpTransfer : public QObject {
    Q_OBJECT
public:
    /** 终态保留结果，应用可以 deleteLater()；finished() 对所有终态只发一次。 */
    enum State {
        Pending,   ///< 尚在准备本地设备或远端句柄。
        Running,   ///< 正在流水线传输文件内容。
        Finishing, ///< 正在关闭远端文件及提交本地文件。
        Succeeded, ///< 文件完整传输并完成关闭。
        Failed,    ///< 失败，详见 errorString()。
        Cancelled  ///< 应用取消，已发往服务器的操作仍可能完成。
    };
    Q_ENUM(State)

    /** 传输错误类别，远端详细状态同时保存在说明中。 */
    enum Error {
        NoError,              ///< 成功，或尚无错误。
        InvalidConfiguration, ///< 路径、缓冲参数或 SFTP 状态不正确。
        LocalIoError,         ///< 本地文件或借用设备读写失败。
        RemoteError,          ///< 远端操作或 SFTP 协议失败。
        TimeoutError,         ///< 本地或远端在时限内没有进展。
        SessionClosed,        ///< 所属 SSH 或 SFTP 子系统已关闭。
        OperationCancelled    ///< 应用取消或销毁传输。
    };
    Q_ENUM(Error)

    /** 传输行为；网络请求窗口由 SFTP Options.maxInFlightBytes 控制。 */
    struct Options {
        bool overwrite = false;              ///< 路径目标已存在时默认失败；QIODevice 入口不执行截断或路径覆盖。
        bool removePartialOnFailure = false; ///< 上传失败或取消后，连接可用时尽力删除部分远端文件，默认保留。
        qint64 bufferSize = 4 * 1024 * 1024; ///< 此传输的预读／乱序结果预算，至少 64 KiB。
        int idleTimeoutMs = 30000;           ///< 本地或远端均无进展的时限，必须大于零。
        QFileDevice::Permissions
            permissions = QFileDevice::ReadOwner | QFileDevice::WriteOwner | QFileDevice::ReadGroup |
                          QFileDevice::ReadOther; ///< 上传创建文件的 Qt rwx 权限，User* 输入作为 Owner* 别名。
    };

    /** 取消传输；外部设备仍归调用方所有。 */
    ~QSshSftpTransfer() override;
    /** 返回 Qt 线程观察到的生命周期。 */
    State state() const;
    /** 返回终止错误类别。 */
    Error error() const;
    /** 返回终止错误说明。 */
    QString errorString() const;
    /** 返回已落入下载目标或已获服务器写入确认的字节数。 */
    qint64 bytesTransferred() const;
    /** 返回预计总字节数，-1 表示未知，0 表示空文件。 */
    qint64 totalBytes() const;
public Q_SLOTS:
    /** 停止补发请求并取消本次传输，其他传输和 SSH channel 继续工作。 */
    void cancel();
    /** 声明顺序上传源不再产生数据；现有缓冲仍会读完，零字节读取本身不代表 EOF。 */
    void finishInput();
Q_SIGNALS:
    /** 状态变为 state。 */
    void stateChanged(QSshSftpTransfer::State state);
    /** 已完成 completed 字节，总量 total 可为 -1；中间通知合并，100% 不代替最终 finished。 */
    void progress(qint64 completed, qint64 total);
    /** 发生终止 error，详情见 errorString()。 */
    void errorOccurred(QSshSftpTransfer::Error error);
    /** 进入成功、失败或取消终态，只发一次。 */
    void finished();

private:
    /** 由 sftp 创建；upload 指示方向，path 为远端路径，local 为本地路径或空值。 */
    QSshSftpTransfer(QSshSftpSession *sftp, bool upload, QString path, QString local, QIODevice *device,
                     const Options &options);
    QScopedPointer<QSshSftpTransferPrivate> d_ptr; ///< 独立 Private，隐藏 worker、文件和协议请求。
    Q_DECLARE_PRIVATE(QSshSftpTransfer)
    Q_DISABLE_COPY(QSshSftpTransfer)
    friend class QSshSftpSession;
};
Q_DECLARE_METATYPE(QSshSftpTransfer::State)
Q_DECLARE_METATYPE(QSshSftpTransfer::Error)
