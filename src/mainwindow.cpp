#include "mainwindow.h"

#include "actions/datakeys.h"

#include <QApplication>
#include <QSplitter>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent) {
    initViews();
    initEvents();
}

void MainWindow::initViews() {
    m_action_system = new ActionSystem(this);
    m_host_tree_model = new HostTreeModel(this);

    m_tab_widget = new TabWidget();
    m_host_tree_view = new HostTreeView(m_action_system);
    m_host_tree_view->setModel(m_host_tree_model);

    m_splitter = new QSplitter();
    m_splitter->setObjectName("RootSplitter");
    m_splitter->setContentsMargins(0, 0, 0, 0);
    m_splitter->setChildrenCollapsible(false);
    m_splitter->addWidget(m_host_tree_view);
    m_splitter->addWidget(m_tab_widget);
    m_splitter->setStretchFactor(0, 0);
    m_splitter->setStretchFactor(1, 1);

    m_statusbar = new QStatusBar();
    m_statusbar->setSizeGripEnabled(false);

    setCentralWidget(m_splitter);
    setStatusBar(m_statusbar);
    resize(800, 600);
    setWindowTitle(QApplication::applicationDisplayName());
}

void MainWindow::initEvents() {}

std::any MainWindow::getData(const QString &dataId) const {
    if (dataId == DataKeys::MAIN_WINDOW.name()) {
        QMainWindow *window = const_cast<MainWindow *>(this);
        return window;
    }

    if (dataId == DataKeys::HOST_TREE_MODEL.name()) {
        QAbstractItemModel *model = m_host_tree_model;
        return model;
    }

    if (dataId == DataKeys::HOST_TREE_VIEW.name()) {
        QTreeView *view = m_host_tree_view;
        return view;
    }

    return {};
}