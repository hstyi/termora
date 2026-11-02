#include "actionupdatescheduler.h"

#include <QEvent>
#include <QScopedValueRollback>
#include <QThread>
#include <stdexcept>
#include <utility>

ActionUpdateScheduler::ActionUpdateScheduler(QObject *parent) : QObject(parent), m_timer(this) {
    m_timer.setSingleShot(true);
    m_timer.setInterval(0);
    connect(&m_timer, &QTimer::timeout, this, &ActionUpdateScheduler::flushUpdates);
}

void ActionUpdateScheduler::registerContainer(QWidget *container, std::function<void()> refresh) {
    Q_ASSERT(thread() == QThread::currentThread());
    if (!container || !refresh || container->thread() != thread()) {
        throw std::invalid_argument("Invalid action update container");
    }

    auto client = m_clients.value(container);
    if (!client) {
        client = std::make_shared<Client>();
        client->container = container;
        m_clients.insert(container, client);
        container->installEventFilter(this);
        client->destroyed_connection = connect(container, &QObject::destroyed, this,
                                               [this, container] { unregisterContainer(container); });
    }

    client->refresh = std::move(refresh);
    client->dirty = true;
    scheduleUpdate();
}

void ActionUpdateScheduler::unregisterContainer(QWidget *container) {
    Q_ASSERT(thread() == QThread::currentThread());
    const auto client = m_clients.take(container);
    if (!client) {
        return;
    }

    if (client->container) {
        client->container->removeEventFilter(this);
    }

    disconnect(client->destroyed_connection);

    // 清空本轮快照中的登记，避免注销后仍然执行尚未调用的回调。
    client->container.clear();
    client->refresh = {};
    client->dirty = false;

    if (m_clients.isEmpty()) {
        m_timer.stop();
    }
}

void ActionUpdateScheduler::requestUpdate() {
    Q_ASSERT(thread() == QThread::currentThread());
    for (const auto &client : m_clients) {
        client->dirty = true;
    }
    scheduleUpdate();
}

bool ActionUpdateScheduler::eventFilter(QObject *watched, QEvent *event) {
    if (event->type() == QEvent::Show) {
        if (const auto client = m_clients.value(qobject_cast<QWidget *>(watched))) {
            client->dirty = true;
            scheduleUpdate();
        }
    }
    return QObject::eventFilter(watched, event);
}

void ActionUpdateScheduler::scheduleUpdate() {
    if (m_updating || m_timer.isActive()) {
        return;
    }

    for (const auto &client : m_clients) {
        if (client->dirty && client->container && client->container->isVisible()) {
            m_timer.start();
            return;
        }
    }
}

void ActionUpdateScheduler::flushUpdates() {
    Q_ASSERT(thread() == QThread::currentThread());
    {
        QScopedValueRollback updating(m_updating, true);
        // 回调可能注册、注销或销毁容器，遍历本轮的快照。
        for (const auto clients = m_clients.values(); const auto &client : clients) {
            if (!client->dirty || !client->container || !client->container->isVisible()) {
                continue;
            }
            client->dirty = false;
            const auto refresh = client->refresh;
            refresh();
        }
    }
    // 更新期间的新请求留到下一轮；隐藏容器等待再次显示。
    scheduleUpdate();
}
