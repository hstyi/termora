#pragma once

#include <QTabWidget>

class TabWidgetModel : public QObject {
    Q_OBJECT
public:
    explicit TabWidgetModel(QTabWidget *parent = nullptr);

public:
    struct Tab {
        QString title;
        QIcon icon;
        QWidget *widget;
        bool closeable;
        void *userdata;
    };

public:
    int count() const;
    const Tab &tab(int index) const;

    void setTitle(int index, const QString &title);
    void setIcon(int index, const QIcon &icon);
    void setUserdata(int index, void *userdata);
    void setWidget(int index, QWidget *widget);
    void setCloseable(int index, bool closeable);

    void setCurrentIndex(int index);
    int currentIndex() const;

    void add(const QString &title, const QIcon &icon, QWidget *widget, void *userdata = nullptr);
    void removeAt(int index);
    void insert(int index, QString title, QIcon icon, QWidget *widget, void *userdata = nullptr);

Q_SIGNALS:
    void tabInserted(int index);
    void tabRemoved(int index);
    void tabChanged(int index);

private:
    QVector<Tab> m_tabs;
    QTabWidget *m_tab_widget = nullptr;
};
