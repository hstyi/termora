#pragma once

#include "itemdelegate.h"

#include <QTreeView>

class TreeView : public QTreeView {
    Q_OBJECT
public:
    explicit TreeView(QWidget *parent = nullptr);

public:
    QRect visualRect(const QModelIndex &index) const override;
    /** 定位到首尾条目时，同时保留对应的滚动留白。 */
    void scrollTo(const QModelIndex &index, ScrollHint hint = EnsureVisible) override;

public:
    class TreeItemDelegate : public ItemDelegate {
    public:
        using ItemDelegate::ItemDelegate;
        QSize sizeHint(const QStyleOptionViewItem &option, const QModelIndex &index) const override;

    protected:
        void initStyleOption(QStyleOptionViewItem *option, const QModelIndex &index) const override;
    };

protected:
#if QT_VERSION < QT_VERSION_CHECK(6, 0, 0)
    QStyleOptionViewItem viewOptions() const override;
#endif
    void drawRow(QPainter *painter, const QStyleOptionViewItem &options, const QModelIndex &index) const override;
    void drawBranches(QPainter *painter, const QRect &rect, const QModelIndex &index) const override;
    QRegion visualRegionForSelection(const QItemSelection &selection) const override;
    void wheelEvent(QWheelEvent *event) override;
    bool event(QEvent *event) override;
    void mousePressEvent(QMouseEvent *event) override;
    /** 布局后恢复独立于统一行高的上下滚动留白。 */
    void updateGeometries() override;

private:
    /** 初始化树的样式、统一行高和滚动留白。 */
    void initViews();
    /** 连接展开折叠和滚动范围变化。 */
    void initEvents();
    /** 扩展像素滚动范围，让上下各 4px 留白随内容滚动。 */
    void updateScrollPadding();

private:
    enum class ScrollAxis {
        Vertical,
        Horizontal
    };

    ScrollAxis m_scroll_axis = ScrollAxis::Vertical;
    qreal m_direction_x = 0;
    qreal m_direction_y = 0;
    int m_pending_cross_delta = 0;
    bool m_updating_scroll_padding = false; // 防止布局和滚动范围变化相互触发。

public:
    QModelIndex indexAt(const QPoint &point) const override;
};
