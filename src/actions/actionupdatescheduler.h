#pragma once

#include <QHash>
#include <QObject>
#include <QPointer>
#include <QTimer>
#include <QWidget>
#include <functional>
#include <memory>

class ActionUpdateScheduler : public QObject {
    Q_OBJECT
public:
    explicit ActionUpdateScheduler(QObject *parent = nullptr);

    void registerContainer(QWidget *container, std::function<void()> refresh);
    void unregisterContainer(QWidget *container);
    void requestUpdate();

public:
    bool eventFilter(QObject *watched, QEvent *event) override;

private:
    struct Client {
        QPointer<QWidget> container;
        std::function<void()> refresh;
        QMetaObject::Connection destroyed_connection;
        bool dirty = true;
    };

    void scheduleUpdate();
    void flushUpdates();

    QHash<QWidget *, std::shared_ptr<Client>> m_clients;
    QTimer m_timer;
    bool m_updating = false;
};
