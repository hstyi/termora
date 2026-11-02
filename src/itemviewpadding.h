#pragma once

#include <QAbstractItemView>
#include <QMargins>
#include <QTreeView>

// Extra space belongs to the first and last visible rows, so Qt scrolls it normally.
inline QMargins itemViewRowPadding(const QAbstractItemView *view, QModelIndex index) {
    if (!view || !index.isValid()) {
        return {};
    }
    const auto *tree = qobject_cast<const QTreeView *>(view);
    // 统一行高的树通过滚动范围提供留白，行本身保持样式指定的高度。
    if (tree && tree->uniformRowHeights()) {
        return {};
    }
    index = index.sibling(index.row(), 0);
    const auto root = view->rootIndex();
    const int top = index.row() == 0 && index.parent() == root ? 4 : 0;
    bool last = !tree || !tree->isExpanded(index) || !index.model()->hasChildren(index);
    for (auto current = index; last && current != root; current = current.parent()) {
        last = current.row() == current.model()->rowCount(current.parent()) - 1;
    }
    return {0, top, 0, last ? 4 : 0};
}
