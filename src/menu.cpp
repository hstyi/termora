#include "menu.h"

#include <QPaintEvent>
#include <QPainter>
#include <QStyleOptionMenuItem>
#include <QWidgetAction>

Menu *Menu::addMenu(const QString &title) {
    auto *menu = new Menu(title, this);
    QMenu::addMenu(menu);
    return menu;
}

void Menu::paintEvent(QPaintEvent *event) {
    QMenu::paintEvent(event);

    QPainter painter(this);
    QStyleOptionMenuItem panel;
    panel.initFrom(this);
    panel.state = QStyle::State_None;

    for (auto *action : actions()) {
        if (!action->isVisible() || action->isSeparator() || qobject_cast<const QWidgetAction *>(action)) {
            continue;
        }

        QStyleOptionMenuItem item;
        initStyleOption(&item, action);
        const auto tab = item.text.indexOf(QLatin1Char('\t'));
        if (tab < 0) {
            continue;
        }

        item.rect = actionGeometry(action);
        const auto clip = event->region().intersected(item.rect).intersected(contentsRect()) - childrenRegion();
        if (clip.isEmpty()) {
            continue;
        }

        // QSS 把快捷键左对齐绘制在预留列中。布局仍保留整列宽度，
        // 仅绘制时按当前快捷键的实际宽度定位，让各行末尾对齐。
        const int shortcut_width = item.fontMetrics.horizontalAdvance(item.text.mid(tab + 1)) + 1;
#if QT_VERSION >= QT_VERSION_CHECK(6, 0, 0)
        item.reservedShortcutWidth = shortcut_width;
#else
        item.tabWidth = shortcut_width;
#endif

        painter.save();
        painter.setClipRegion(clip);
        // 先恢复面板背景，再交给同一套 QSS 重绘，保留选中、禁用、图标及箭头样式。
        style()->drawPrimitive(QStyle::PE_PanelMenu, &panel, &painter, this);
        style()->drawControl(QStyle::CE_MenuItem, &item, &painter, this);
        painter.restore();
    }
}
