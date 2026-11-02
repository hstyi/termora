#include "tabwidgetmodel.h"

#include <utility>

TabWidgetModel::TabWidgetModel(QTabWidget *parent) : QObject(parent) {
    m_tab_widget = parent;
}

int TabWidgetModel::count() const {
    return m_tabs.count();
}

const TabWidgetModel::Tab &TabWidgetModel::tab(int index) const {
    return m_tabs[index];
}

void TabWidgetModel::setTitle(int index, const QString &title) {
    m_tabs[index].title = title;
    Q_EMIT tabChanged(index);
}

void TabWidgetModel::setIcon(int index, const QIcon &icon) {
    m_tabs[index].icon = icon;
    Q_EMIT tabChanged(index);
}

void TabWidgetModel::setUserdata(int index, void *userdata) {
    m_tabs[index].userdata = userdata;
    Q_EMIT tabChanged(index);
}

void TabWidgetModel::setWidget(int index, QWidget *widget) {
    m_tabs[index].widget = widget;
    Q_EMIT tabChanged(index);
}

void TabWidgetModel::setCloseable(int index, bool closeable) {
    m_tabs[index].closeable = closeable;
    Q_EMIT tabChanged(index);
}

void TabWidgetModel::setCurrentIndex(int index) {
    m_tab_widget->setCurrentIndex(index);
}

int TabWidgetModel::currentIndex() const {
    return m_tab_widget->currentIndex();
}

void TabWidgetModel::add(const QString &title, const QIcon &icon, QWidget *widget, void *userdata) {
    insert(count(), title, icon, widget, userdata);
}

void TabWidgetModel::removeAt(int index) {
    m_tabs.removeAt(index);
    Q_EMIT tabRemoved(index);
}

void TabWidgetModel::insert(int index, QString title, QIcon icon, QWidget *widget, void *userdata) {
    m_tabs.push_back({.title = std::move(title),
                      .icon = std::move(icon),
                      .widget = widget,
                      .closeable = true,
                      .userdata = userdata});
    Q_EMIT tabInserted(index);
}