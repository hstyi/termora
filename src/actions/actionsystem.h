#pragma once

#include "actionupdatescheduler.h"
#include "anaction.h"

#include <QAction>
#include <QHash>
#include <QObject>
#include <QPointer>
#include <QTimer>
#include <QWidget>
#include <functional>
#include <memory>

class ActionSystem : public QObject {
    Q_OBJECT
public:
    explicit ActionSystem(QObject *parent = nullptr);
    ~ActionSystem() override = default;

public:
    void registerAction(const QString &id, std::unique_ptr<AnAction> action);
    AnAction *getAction(const QString &id) const;
    bool updateAction(AnAction *action, ActionEvent &event) const;
    bool executeAction(const QString &id, ActionEvent &event);
    bool executeAction(AnAction *action, ActionEvent &event);

    void registerUpdateContainer(QWidget *container, std::function<void()> refresh);
    void unregisterUpdateContainer(QWidget *container);
    void requestUpdate();

private:
    struct Definition {
        std::unique_ptr<AnAction> action;
    };

    struct Binding {
        QString id;
        std::shared_ptr<Definition> definition;

        QPointer<QObject> source;
        QPointer<QWidget> owner;

        ActionPlace place;
    };

private:
    QHash<QString, std::shared_ptr<Definition>> m_actions;
    ActionUpdateScheduler *m_update_scheduler = nullptr;
};
