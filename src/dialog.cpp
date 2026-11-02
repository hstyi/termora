#include "dialog.h"

#include <QGuiApplication>
#include <QLabel>
#include <QTimer>
#include <QVBoxLayout>
#include <QWKWidgets/widgetwindowagent.h>

Dialog::Dialog(QWidget *parent) : QDialog(parent) {
    auto p = palette();
    p.setColor(QPalette::Window, Qt::white);
    setPalette(p);
    setAutoFillBackground(true);
}

HeadlessDialog::HeadlessDialog(const bool has_title, QWidget *parent) : Dialog(parent) {
    if (!has_title) {
        // A titleless dialog has no system controls that need a reserved safe area.
        setAttribute(Qt::WA_ContentsMarginsRespectsSafeArea, false);
    }

    // offscreen/minimal 没有原生窗口句柄，不能交给 QWK 的 Cocoa/Win32 窗口上下文。
    if (const auto platform = QGuiApplication::platformName(); platform != "offscreen" && platform != "minimal") {
        m_widget_window_agent = new QWK::WidgetWindowAgent(this);
        m_widget_window_agent->setup(this);
        m_widget_window_agent->setWindowAttribute("no-system-buttons", true);
    }

    if (has_title) {
        auto *titleLabel = new QLabel(windowTitle(), this);
        auto font = titleLabel->font();
        font.setBold(true);
        titleLabel->setFont(font);
        titleLabel->setAlignment(Qt::AlignCenter);
        titleLabel->setFixedHeight(28);

        connect(this, &QWidget::windowTitleChanged, titleLabel, &QLabel::setText);
        if (m_widget_window_agent) {
            m_widget_window_agent->setTitleBar(titleLabel);
        }

        auto *layout = new QVBoxLayout(this);
        layout->setSpacing(0);
        layout->setContentsMargins(0, 0, 0, 0);
        layout->addWidget(titleLabel);
        layout->addStretch();
    }
}

void HeadlessDialog::showEvent(QShowEvent *event) {
    Dialog::showEvent(event);
    if (!m_widget_window_agent) {
        return;
    }
    QTimer::singleShot(0, this, [this] {
        if (const int height = m_widget_window_agent->windowAttribute("title-bar-height").toInt(); height > 0) {
            if (const auto title_bar = m_widget_window_agent->titleBar()) {
                title_bar->setFixedHeight(height);
            }
        }
    });
}
