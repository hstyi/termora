#pragma once

#include <QHeaderView>
#include <QStyledItemDelegate>
#include <QTableWidget>

class TableItemDelegate : public QStyledItemDelegate {
public:
    using QStyledItemDelegate::QStyledItemDelegate;

protected:
    void initStyleOption(QStyleOptionViewItem *option, const QModelIndex &index) const override {
        QStyledItemDelegate::initStyleOption(option, index);
        // Qt 5 的 Table 不设置首尾列状态，补齐后 QSS 的 :last / :only-one 才能生效。
        const auto *table = qobject_cast<const QTableWidget *>(parent());
        const auto *header = table->horizontalHeader();
        const int position = header->visualIndex(index.column());
        if (header->count() == 1) {
            option->viewItemPosition = QStyleOptionViewItem::OnlyOne;
        } else if (position == 0) {
            option->viewItemPosition = QStyleOptionViewItem::Beginning;
        } else if (position == header->count() - 1) {
            option->viewItemPosition = QStyleOptionViewItem::End;
        } else {
            option->viewItemPosition = QStyleOptionViewItem::Middle;
        }
    }
};

class TableWidget : public QTableWidget {
    Q_OBJECT
public:
    explicit TableWidget(QWidget *parent = nullptr);
};