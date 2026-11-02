#include "actionbutton.h"

#include <stdexcept>
#include <utility>

ActionButton::ActionButton(ActionSystem *action_system, AnAction *action, QObject *context_source,
                           const ActionPlace place, QWidget *parent)
    : QToolButton(parent),
      m_action_system(action_system),
      m_action(action),
      m_context(context_source),
      m_place(place) {
    if (!action_system || !action) {
        throw std::invalid_argument("Invalid action button binding");
    }

    connect(this, &QToolButton::clicked, this, &ActionButton::triggerAction);

    connect(action_system, &QObject::destroyed, this, [this] {
        m_action = nullptr;
        m_presentation.enabled = false;
        setEnabled(false);
    });

    // 构造期间只初始化展示；挂入界面后再进行上下文刷新。
    m_presentation = m_action->templatePresentation();
    m_action->applyTextOverride(m_place, m_presentation);

    setText(m_presentation.text);
    setToolTip(m_presentation.toolTip);
    setIcon(m_presentation.icon);
    setEnabled(m_presentation.enabled);

    if (!m_presentation.visible) {
        hide();
    }
}

void ActionButton::triggerAction() {
    if (!m_action_system || !m_action) {
        return;
    }

    auto presentation = m_presentation;
    ActionEvent event(m_context, presentation, m_place);
    const QPointer<ActionButton> guard(this);
    if (!m_action_system->executeAction(m_action, event) && guard) {
        // 执行入口会重新校验；拒绝执行时也同步最新状态。
        guard->m_presentation = std::move(presentation);
        guard->applyPresentation();
    }
}

void ActionButton::refreshPresentation() {
    if (!m_action_system || !m_action) {
        setEnabled(false);
        return;
    }

    auto presentation = m_presentation;
    ActionEvent event(m_context, presentation, m_place);
    m_action_system->updateAction(m_action, event);

    m_presentation = std::move(presentation);
    applyPresentation();
}

void ActionButton::applyPresentation() {
    setText(m_presentation.text);
    setToolTip(m_presentation.toolTip);
    setIcon(m_presentation.icon);
    setEnabled(m_presentation.enabled);
    setVisible(m_presentation.visible);
}
