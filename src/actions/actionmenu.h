#pragma once

#include "../menu.h"
#include "actionsystem.h"

class ActionMenu : public Menu {
    Q_OBJECT
public:
    explicit ActionMenu(ActionSystem *action_system, QObject *context_source, ActionPlace place,
                        QWidget *parent = nullptr);

public:
    QAction *addActionId(const QString &id);
    QAction *addActionId(const QString &id, AnAction *action);

public:
    virtual ActionMenu *addActionMenu(const QString &title);

private:
    struct Item {
        QString id;
        QPointer<QAction> qaction;
        AnAction *action;
        Presentation presentation;
    };

public:
    void updateActions() const;

private:
    void applyPresentation(const std::shared_ptr<Item> &item) const;

protected:
    ActionPlace m_place;
    ActionSystem *m_action_system = nullptr;
    QObject *m_context_source = nullptr;

private:
    std::vector<std::shared_ptr<Item>> m_items;
};
