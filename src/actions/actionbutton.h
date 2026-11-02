#pragma once

#include "actionsystem.h"
#include "presentation.h"

#include <QPointer>
#include <QToolButton>

class ActionButton : public QToolButton {
    Q_OBJECT
public:
    explicit ActionButton(ActionSystem *action_system, AnAction *action, QObject *context_source, ActionPlace place,
                          QWidget *parent = nullptr);

    void refreshPresentation();

private:
    void triggerAction();
    void applyPresentation();

private:
    Presentation m_presentation;
    ActionSystem *m_action_system = nullptr;
    AnAction *m_action = nullptr;
    DataContext m_context;
    ActionPlace m_place = ActionPlace::Toolbar;
};
