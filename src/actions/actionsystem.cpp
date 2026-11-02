#include "actionsystem.h"

#include "actionupdatescheduler.h"
#include "executes/newhostaction.h"

#include <QThread>
#include <stdexcept>
#include <utility>

ActionSystem::ActionSystem(QObject *parent) : QObject(parent), m_update_scheduler(new ActionUpdateScheduler(this)) {
    registerAction("host.new", std::make_unique<NewHostAction>());
}

void ActionSystem::registerAction(const QString &id, std::unique_ptr<AnAction> action) {
    if (id.isEmpty() || !action) {
        throw std::invalid_argument("Invalid action registration");
    }

    if (m_actions.contains(id)) {
        throw std::invalid_argument("Action ID already registered");
    }

    const auto definition = std::make_shared<Definition>();
    definition->action = std::move(action);

    m_actions.insert(id, definition);
}

AnAction *ActionSystem::getAction(const QString &id) const {
    const auto definition = m_actions.value(id);
    return definition ? definition->action.get() : nullptr;
}

bool ActionSystem::updateAction(AnAction *action, ActionEvent &event) const {
    Q_ASSERT(thread() == QThread::currentThread());
    auto &presentation = event.presentation();
    if (!action) {
        presentation.enabled = false;
        return false;
    }

    presentation = action->templatePresentation();
    action->applyTextOverride(event.place(), presentation);
    action->update(event);
    return true;
}

bool ActionSystem::executeAction(const QString &id, ActionEvent &event) {
    return executeAction(getAction(id), event);
}

bool ActionSystem::executeAction(AnAction *action, ActionEvent &event) {
    if (!updateAction(action, event)) {
        return false;
    }
    const auto &presentation = event.presentation();
    if (!presentation.enabled || !presentation.visible) {
        return false;
    }

    const QPointer guard(this);
    action->execute(event);
    if (guard) {
        guard->requestUpdate();
    }
    return true;
}

void ActionSystem::registerUpdateContainer(QWidget *container, std::function<void()> refresh) {
    m_update_scheduler->registerContainer(container, std::move(refresh));
}

void ActionSystem::unregisterUpdateContainer(QWidget *container) {
    m_update_scheduler->unregisterContainer(container);
}

void ActionSystem::requestUpdate() {
    m_update_scheduler->requestUpdate();
}
