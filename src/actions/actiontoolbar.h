#pragma once
#include "actionbutton.h"
#include "actionsystem.h"

#include <QBoxLayout>
#include <QWidget>

class ActionToolBar : public QWidget {
    Q_OBJECT
public:
    explicit ActionToolBar(ActionSystem *action_system, QObject *context_source, Qt::Orientation orientation,
                           QWidget *parent = nullptr);

public:
    ActionButton *addActionId(const QString &id);

public:
    void updateActions() const;

private:
    QBoxLayout *m_layout = nullptr;
    ActionSystem *m_action_system = nullptr;
    QObject *m_context_source = nullptr;
};
