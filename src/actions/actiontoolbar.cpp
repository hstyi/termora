#include "actiontoolbar.h"

#include <stdexcept>

ActionToolBar::ActionToolBar(ActionSystem *action_system, QObject *context_source, Qt::Orientation orientation,
                             QWidget *parent)
    : QWidget(parent),
      m_action_system(action_system),
      m_context_source(context_source) {
    m_layout = new QBoxLayout(orientation == Qt::Horizontal ? QBoxLayout::LeftToRight : QBoxLayout::TopToBottom, this);
    m_layout->setSpacing(4);
    m_layout->setContentsMargins(0, 0, 0, 0);
    m_action_system->registerUpdateContainer(this, [this] { updateActions(); });
}

ActionButton *ActionToolBar::addActionId(const QString &id) {
    auto *action = m_action_system ? m_action_system->getAction(id) : nullptr;
    if (!action) {
        throw std::invalid_argument("Unknown action ID");
    }
    auto *action_button = new ActionButton(m_action_system, action, m_context_source, ActionPlace::Toolbar, this);
    action_button->setIconSize(QSize(16, 16));
    action_button->setFixedSize(QSize(22, 22));
    action_button->setSizePolicy(QSizePolicy::Policy::Preferred, QSizePolicy::Policy::Preferred);
    action_button->setFocusPolicy(Qt::NoFocus);
    action_button->setToolButtonStyle(Qt::ToolButtonIconOnly);
    m_layout->addWidget(action_button);
    m_action_system->requestUpdate();
    return action_button;
}

void ActionToolBar::updateActions() const {
    for (int i = 0; i < m_layout->count(); ++i) {
        if (auto *button = qobject_cast<ActionButton *>(m_layout->itemAt(i)->widget())) {
            button->refreshPresentation();
        }
    }
}
