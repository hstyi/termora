#pragma once

#include <QDialog>
#include <QWKWidgets/widgetwindowagent.h>

class Dialog : public QDialog {
    Q_OBJECT
public:
    explicit Dialog(QWidget *parent = nullptr);
    ~Dialog() override = default;
};

class HeadlessDialog : public Dialog {
    Q_OBJECT
public:
    explicit HeadlessDialog(bool has_title = true, QWidget *parent = nullptr);

protected:
    void showEvent(QShowEvent *event) override;

private:
    QWK::WidgetWindowAgent *m_widget_window_agent = nullptr;
};