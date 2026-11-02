#pragma once

#include "actions/actionsystem.h"
#include "treeview.h"

class HostTreeView : public TreeView {
    Q_OBJECT
public:
    explicit HostTreeView(ActionSystem *action_system, QWidget *parent = nullptr);

private:
    void initViews();
    void initEvents();

private:
    ActionSystem *m_action_system = nullptr;
};