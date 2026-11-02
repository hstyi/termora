#include "actionmenu.h"

#include <stdexcept>
#include <utility>

ActionMenu::ActionMenu(ActionSystem *action_system, QObject *context_source, const ActionPlace place, QWidget *parent)
    : Menu(parent),
      m_place(place),
      m_action_system(action_system),
      m_context_source(context_source) {
    connect(this, &QMenu::aboutToShow, this, &ActionMenu::updateActions);
}

QAction *ActionMenu::addActionId(const QString &id) {
    auto *action = m_action_system ? m_action_system->getAction(id) : nullptr;

    if (!action) {
        throw std::invalid_argument("Unknown action ID");
    }

    return addActionId(id, action);
}

QAction *ActionMenu::addActionId(const QString &id, AnAction *action) {
    auto item = std::make_shared<Item>();
    item->id = id;

    // 创建这个菜单项的展示副本。
    item->presentation = action->templatePresentation();
    item->qaction = addAction(item->presentation.text);
    item->action = action;

    if (m_place == ActionPlace::MainMenu) {
        if (const auto shortcuts = action->shortcuts(); !shortcuts.isEmpty()) {
            item->qaction->setShortcuts(shortcuts);
            item->qaction->setShortcutContext(Qt::WindowShortcut);
        }
    }

    m_items.push_back(item);
    applyPresentation(item);

    connect(item->qaction.data(), &QAction::triggered, this, [this, item](bool) {
        if (!m_action_system) {
            return;
        }
        auto presentation = item->presentation;
        ActionEvent event(DataContext(m_context_source), presentation, m_place);
        if (!m_action_system->executeAction(item->action, event)) {
            item->presentation = std::move(presentation);
            applyPresentation(item);
        }
    });

    return item->qaction.data();
}

ActionMenu *ActionMenu::addActionMenu(const QString &title) {
    auto *menu = new ActionMenu(m_action_system, m_context_source, m_place, this);
    addMenu(menu);
    return menu;
}

void ActionMenu::updateActions() const {
    for (const auto &item : m_items) {
        if (!item->qaction || !item->action) {
            continue;
        }

        auto presentation = item->presentation;

        if (m_action_system) {
            ActionEvent event(DataContext(m_context_source), presentation, m_place);
            m_action_system->updateAction(item->action, event);
        } else {
            presentation.enabled = false;
        }

        item->presentation = std::move(presentation);
        applyPresentation(item);
    }
}

void ActionMenu::applyPresentation(const std::shared_ptr<Item> &item) const {
    auto *action = item->qaction.data();
    if (!action) {
        return;
    }

    const auto &p = item->presentation;
    action->setText(p.text);
    action->setToolTip(p.toolTip);
    if (m_place != ActionPlace::MainMenu) {
        action->setIcon(p.icon);
    }
    action->setEnabled(p.enabled);
    action->setVisible(p.visible);
    action->setChecked(p.checked);
}
