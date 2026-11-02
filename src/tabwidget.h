#pragma once

#include "tabwidgetmodel.h"

#include <QTabWidget>

class TabWidget : public QTabWidget {
    Q_OBJECT
public:
    explicit TabWidget(QWidget *parent = nullptr);

public:
    TabWidgetModel *model();

private:
    void initViews();
    void initEvents();

private:
    TabWidgetModel *m_model = nullptr;
};