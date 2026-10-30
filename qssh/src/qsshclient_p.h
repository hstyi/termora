#pragma once

#include <asio.hpp>
#include <functional>
#include <future>
#include <qssh/qsshclient.h>
#include <thread>

/** Client 的 Qt 私有实现，事件循环恰好由一个显式 worker 执行。 */
class QSshClientPrivate {
public:
    /** 为 owner 初始化 libssh 和运行线程。 */
    explicit QSshClientPrivate(QSshClient *owner);
    /** 排空取消回调并回收线程；此时所有 Session 必须已经销毁。 */
    ~QSshClientPrivate();
    /** 将 task 投递给 worker，仅用于 QObject 析构时的同步资源回收。 */
    void synchronize(std::function<void()> task);

    QSshClient *q;                                                   ///< 所属前端对象。
    asio::io_context io;                                             ///< 全部连接共享的事件循环。
    asio::executor_work_guard<asio::io_context::executor_type> work; ///< 防止空闲时 run() 退出。
    std::thread worker;                                              ///< 唯一执行 SSH 协议和 socket 调度的线程。
    bool closing = false;                                            ///< Qt 线程独占，close() 后拒绝创建连接。
};
