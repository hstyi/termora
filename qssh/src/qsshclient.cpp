#include "qsshclient_p.h"

#include <QThread>
#include <libssh/libssh.h>
#include <qssh/qsshsession.h>
#include <stdexcept>

namespace {
    /** 进程级 libssh 生命周期；静态链接也在启动任何 SSH worker 之前完成初始化。 */
    struct LibsshRuntime {
        /** 初始化加密和线程支持，失败时阻止创建运行环境。 */
        LibsshRuntime() {
            if (ssh_init() != SSH_OK) {
                throw std::runtime_error("Unable to initialize libssh");
            }
        }

        /** 所有 Client 析构后释放 libssh 的进程级资源。 */
        ~LibsshRuntime() {
            ssh_finalize();
        }
    };
} // namespace

QSshClientPrivate::QSshClientPrivate(QSshClient *owner) : q(owner), work(asio::make_work_guard(io)) {
    static LibsshRuntime runtime;
    worker = std::thread([this] { io.run(); });
}

QSshClientPrivate::~QSshClientPrivate() {
    // 不调用 io.stop()：取消完成回调仍须执行，直到不再持有任何异步操作。
    work.reset();
    worker.join();
}

void QSshClientPrivate::synchronize(std::function<void()> task) {
    std::promise<void> completion;
    auto result = completion.get_future();
    asio::post(io, [task = std::move(task), &completion] {
        task();
        completion.set_value();
    });
    result.get();
}

QSshClient::QSshClient(QObject *parent) : QObject(parent), d_ptr(new QSshClientPrivate(this)) {}

QSshClient::~QSshClient() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshClient);
    d->closing = true;
    // QObject 默认在基类析构时才删除子对象；这里必须让 Session 先于 worker 销毁。
    // 每次重新查找，允许 destroyed 槽提前删除兄弟对象，不保留可能失效的裸指针列表。
    while (auto *session = findChild<QSshSession *>(QString(), Qt::FindDirectChildrenOnly)) {
        delete session;
    }
}

QSshSession *QSshClient::createSession() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshClient);
    return d->closing ? nullptr : new QSshSession(this);
}

void QSshClient::close() {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshClient);
    if (d->closing) {
        return;
    }
    d->closing = true;
    for (const auto sessions = findChildren<QSshSession *>(QString(), Qt::FindDirectChildrenOnly);
         auto *session : sessions) {
        session->close();
    }
}
