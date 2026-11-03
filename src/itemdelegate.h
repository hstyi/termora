#pragma once

#include <QApplication>
#include <QPainter>
#include <QStyle>
#include <QStyleOptionViewItem>
#include <QStyledItemDelegate>

class ItemDelegate : public QStyledItemDelegate {
public:
    using QStyledItemDelegate::QStyledItemDelegate;

    void paint(QPainter *painter, const QStyleOptionViewItem &option, const QModelIndex &index) const override {
        QStyleOptionViewItem opt(option);
        initStyleOption(&opt, index);
        const auto *widget = opt.widget;
        const auto *style = widget ? widget->style() : QApplication::style();

        const bool inactiveSelection = (opt.state & QStyle::State_Selected) && !(opt.state & QStyle::State_Active) &&
                                       (opt.state & QStyle::State_Enabled);
        if (!inactiveSelection || opt.icon.isNull()) {
            style->drawControl(QStyle::CE_ItemViewItem, &opt, painter, widget);
            return;
        }

        // Qt 默认对所有选中项使用 QIcon::Selected，包括失焦选中项。
        // 保留选中状态和图标占位，仅单独按 Normal 模式绘制图标。
        const QIcon icon = opt.icon;
        const QRect iconRect = style->subElementRect(QStyle::SE_ItemViewItemDecoration, &opt, widget);
        opt.icon = QIcon();
        painter->save();
        style->drawControl(QStyle::CE_ItemViewItem, &opt, painter, widget);
        icon.paint(painter, iconRect, opt.decorationAlignment, QIcon::Normal,
                   opt.state & QStyle::State_Open ? QIcon::On : QIcon::Off);
        painter->restore();
    }
};
