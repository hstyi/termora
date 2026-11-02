#include "windowsmenu.h"

#ifdef Q_OS_WINDOWS
#include <dwmapi.h>
#include <windows.h>
#pragma comment(lib, "dwmapi.lib")
#endif

#include <QEvent>
#include <QOperatingSystemVersion>
#include <QWidget>
#include <QWindow>

namespace {
    bool isPopupWidget(const QWidget *widget) {
        return widget &&
               (widget->inherits("QMenu") || (widget->inherits("QFrame") && (widget->windowType() == Qt::Popup ||
                                                                             widget->windowType() == Qt::ToolTip)));
    }

    bool isWindows11() {
#ifdef Q_OS_WINDOWS
        const auto current = QOperatingSystemVersion::current();
        return current.majorVersion() >= 10 && current.microVersion() >= 22000;
#endif
        return false;
    }

    void decoration(QWidget *widget) {
        widget->setWindowFlags(widget->windowFlags() | Qt::FramelessWindowHint
#ifdef Q_OS_WINDOWS
                               | Qt::NoDropShadowWindowHint
#endif
        );
        widget->setAttribute(Qt::WA_TranslucentBackground, !isWindows11());
        widget->setAttribute(Qt::WA_StyledBackground);
    }

#ifdef Q_OS_WINDOWS
    void decorationOnWindow(const QWidget *widget) {
        if (isWindows11()) {
            // Do not create a new native window while handling WinIdChange during destruction.
            if (const auto hwnd = reinterpret_cast<HWND>(widget->internalWinId())) {
                const DWMNCRENDERINGPOLICY policy = DWMNCRP_ENABLED;
                DwmSetWindowAttribute(hwnd, DWMWA_NCRENDERING_POLICY, &policy, sizeof(policy));
                constexpr MARGINS margins = {.cxLeftWidth = 1,
                                             .cxRightWidth = 1,
                                             .cyTopHeight = 1,
                                             .cyBottomHeight = 1};
                DwmExtendFrameIntoClientArea(hwnd, &margins);
                /*const DWM_WINDOW_CORNER_PREFERENCE corners = DWMWCP_ROUNDSMALL;
                DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &corners, sizeof(corners));*/
            }
        }
    }
#endif
} // namespace

WindowsMenu::WindowsMenu(QObject *parent) : QObject(parent) {}

bool WindowsMenu::eventFilter(QObject *watched, QEvent *event) {
    const auto widget = qobject_cast<QWidget *>(watched);
    if (event->type() == QEvent::Polish && isPopupWidget(widget)) {
        decoration(widget);
    }

#ifdef Q_OS_WINDOWS
    if (event->type() == QEvent::WinIdChange && isPopupWidget(widget)) {
        decorationOnWindow(widget);
    }

    if (event->type() == QEvent::Show) {
        // QWidget::Show precedes Qt's native window setup, which resets the DWM margins.
        // Restore them on QWindow::Show, after setup and before the popup becomes visible.
        if (const auto window = qobject_cast<QWindow *>(watched); window && window->handle()) {
            if (const auto popup = QWidget::find(window->winId()); isPopupWidget(popup)) {
                decorationOnWindow(popup);
            }
        }
    }
#endif

    return QObject::eventFilter(watched, event);
}
