#pragma once

#include <asio.hpp>
#include <functional>
#include <libssh/libssh.h>
#include <memory>
#include <qssh/qsshtunnel.h>
#include <vector>

class QSshSessionPrivate;
class QSshForwardStream;

/** Qt 隧道私有实现；网络资源和 libssh 全局请求仅在 Session 的 worker 上操作。 */
class QSshTunnelPrivate {
public:
    /** 为 owner 绑定所属 session，初始化监听器与操作时限。 */
    QSshTunnelPrivate(QSshTunnel *owner, QSshSession *session);
    /** 在 worker 回收监听器和连接，并使迟到的全局请求回调失效。 */
    ~QSshTunnelPrivate();
    /** 将 task 投递到 worker，析构后自动忽略。 */
    void submit(std::function<void()> task);
    /** 设置后台状态 value，将 port 作为监听成功时的实际端口通知前端。 */
    void setState(QSshTunnel::State value, quint16 port = 0);
    /** 报告 error/message，fatal 表示同时关闭隧道。 */
    void fail(QSshTunnel::Error error, const QString &message, bool fatal = true);
    /** 使用 configuration 开启监听或提交远端请求。 */
    void start(QSshTunnel::Options configuration);
    /** 接受本地 TCP 连接，为每条连接创建独立转发流。 */
    asio::awaitable<void> doAccept();
    /** 取消监听并关闭全部关联流，sessionClosed 时不再提交远端取消请求。 */
    void shutdown(bool sessionClosed = false);
    /** 将服务器已经打开的 channel 连接到配置的本地目标。 */
    void acceptRemote(ssh_channel channel);

    QSshTunnel *q;                                              ///< 所属 Qt 对象。
    QSshSessionPrivate *session;                                ///< 所属 Session 私有实现，晚于隧道销毁。
    QSshTunnel::State frontState = QSshTunnel::Idle;            ///< Qt 线程独占的状态快照。
    QSshTunnel::Error frontError = QSshTunnel::NoError;         ///< Qt 线程独占的错误类别。
    QString frontErrorText;                                     ///< Qt 线程独占的错误说明。
    quint16 frontPort = 0;                                      ///< Qt 线程独占的实际监听端口。
    bool frontClosing = false;                                  ///< Qt 线程独占，抑制关闭后的监听成功通知。
    std::shared_ptr<bool> alive = std::make_shared<bool>(true); ///< worker 独占的存活标志。
    asio::ip::tcp::acceptor acceptor;                           ///< Local/Dynamic 使用的本地监听器。
    asio::steady_timer deadline;                                ///< 远端打开及关闭请求时限。
    QSshTunnel::Options options;                                ///< worker 独占的监听和目标配置。
    QSshTunnel::State phase = QSshTunnel::Idle;                 ///< worker 独占的生命周期。
    quint16 boundPort = 0;                                      ///< worker 独占的实际监听端口。
    std::vector<std::weak_ptr<QSshForwardStream>> connections;  ///< 关联流，由 Session 持有强引用。
};
