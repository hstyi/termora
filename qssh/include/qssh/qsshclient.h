#pragma once

#include <QObject>
#include <QScopedPointer>

class QSshClientPrivate;
class QSshSession;
class QSshSessionPrivate;
class QSshChannelPrivate;

/**
 * SSH 运行环境；一个 Client 的全部 Session 共用一个 Asio worker。
 * 公共接口与对象销毁均在所属 Qt 线程执行，该线程必须运行事件循环。
 * Session 归 Client 所有；不要移动 Client、Session、Channel 到其他线程或更换父对象。
 * 析构取消连接并回收 worker；公共接口不依赖 Asio 或 libssh。
 */
class QSshClient : public QObject {
    Q_OBJECT
public:
    /** 创建运行环境；parent 管理本对象的生命周期。 */
    explicit QSshClient(QObject *parent = nullptr);
    /** 销毁所属 Session，在 worker 释放资源后回收线程。 */
    ~QSshClient() override;
    /** 创建尚未连接的 Session，所有权归本 Client；close() 后返回 nullptr。 */
    QSshSession *createSession();
    /** 请求关闭所有 Session，之后不再接受新 Session；可以重复调用。 */
    void close();

private:
    QScopedPointer<QSshClientPrivate> d_ptr; ///< 隐藏事件循环、线程和任务调度实现。
    Q_DECLARE_PRIVATE(QSshClient)
    Q_DISABLE_COPY(QSshClient)
    friend class QSshSessionPrivate;
    friend class QSshChannelPrivate;
};
