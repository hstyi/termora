#pragma once

#include "actions/actionsystem.h"
#include "hosttreemodel.h"
#include "hosttreeview.h"
#include "tabwidget.h"

#include <QMainWindow>
#include <QSplitter>
#include <QStatusBar>

class MainWindow : public QMainWindow, public DataProvider {
    Q_OBJECT
public:
    explicit MainWindow(QWidget *parent = nullptr);

private:
    void initViews();
    void initEvents();

public:
    std::any getData(const QString &dataId) const override;

private:
    QSplitter *m_splitter = nullptr;
    TabWidget *m_tab_widget = nullptr;
    HostTreeView *m_host_tree_view = nullptr;
    HostTreeModel *m_host_tree_model = nullptr;
    QStatusBar *m_statusbar = nullptr;
    ActionSystem *m_action_system = nullptr;
};