#include "hosttreeview.h"

#include "actions/actionmenu.h"
#include "quuid.h"
#include "stores/hoststore.h"

HostTreeView::HostTreeView(ActionSystem *action_system, QWidget *parent)
    : TreeView(parent),
      m_action_system(action_system) {
    initViews();
    initEvents();
}

void HostTreeView::initViews() {
    setContextMenuPolicy(Qt::CustomContextMenu);
}

void HostTreeView::initEvents() {
    connect(this, &QTreeView::customContextMenuRequested, this, [this](const QPoint &pos) {
        ActionMenu menu(m_action_system, this, ActionPlace::ContextMenu);
        menu.addActionId("host.new");
        menu.exec(mapToGlobal(pos));
    });
}