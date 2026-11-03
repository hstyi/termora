#include "tabwidget.h"

#include "qtabbar.h"

#include <QTextEdit>

TabWidget::TabWidget(QWidget *parent) : QTabWidget(parent) {
    initViews();
    initEvents();
}

TabWidgetModel *TabWidget::model() {
    return m_model;
}

void TabWidget::initViews() {
    setTabsClosable(true);
    setUsesScrollButtons(true);
    setElideMode(Qt::ElideRight);
    tabBar()->setDrawBase(false);
    tabBar()->setExpanding(false);
    m_model = new TabWidgetModel(this);
}

void TabWidget::initEvents() {
    connect(m_model, &TabWidgetModel::tabInserted, this, [this](const int index) {
        const auto &tab = m_model->tab(index);
        insertTab(index, tab.widget, tab.icon, tab.title);
        tabBar()->tabButton(index, QTabBar::RightSide)->setVisible(tab.closeable);
    });

    connect(m_model, &TabWidgetModel::tabRemoved, this, [this](const int index) { removeTab(index); });

    connect(m_model, &TabWidgetModel::tabChanged, this, [this](const int index) {
        const auto &tab = m_model->tab(index);
        setTabIcon(index, tab.icon);
        setTabText(index, tab.title);
        tabBar()->tabButton(index, QTabBar::RightSide)->setVisible(tab.closeable);
    });
}
