#include "treeview.h"

#include "itemviewpadding.h"
#include "macscrollstyle.h"

#include <QHeaderView>
#include <QMouseEvent>
#include <QPainter>
#include <QScopedValueRollback>
#include <QScrollBar>
#include <QStyledItemDelegate>
#include <QWheelEvent>

#ifdef Q_OS_WIN
#include <QCoreApplication>
#endif

namespace {
    /** 内容上下各 4px 留白独立于条目的统一行高。 */
    constexpr int kScrollPadding = 4;
} // namespace

TreeView::TreeView(QWidget *parent) : QTreeView(parent) {
    initViews();
    initEvents();
}

void TreeView::initViews() {
    header()->setVisible(false);
    header()->setStretchLastSection(false);
    header()->setSectionResizeMode(QHeaderView::ResizeToContents);

    setItemDelegate(new TreeItemDelegate(this));
    setWordWrap(false);
    setUniformRowHeights(true);

    setIconSize(QSize(16, 16));

    setSelectionMode(SingleSelection);
    setSelectionBehavior(SelectRows);

    setHorizontalScrollMode(ScrollPerPixel);
    setVerticalScrollMode(ScrollPerPixel);
    MacScrollStyle::use(this);
    updateScrollPadding();
}

void TreeView::initEvents() {
    connect(this, &QTreeView::expanded, this, [this] { scheduleDelayedItemsLayout(); });
    connect(this, &QTreeView::collapsed, this, [this] { scheduleDelayedItemsLayout(); });
    connect(verticalScrollBar(), &QScrollBar::rangeChanged, this, [this] { updateScrollPadding(); });
}

void TreeView::updateGeometries() {
    QTreeView::updateGeometries();
    updateScrollPadding();
}

void TreeView::updateScrollPadding() {
    auto *bar = verticalScrollBar();
    if (m_updating_scroll_padding || !uniformRowHeights() || verticalScrollMode() != ScrollPerPixel ||
        bar->minimum() != 0) {
        return;
    }
    QScopedValueRollback<bool> updating(m_updating_scroll_padding, true);

    // Qt 直接用滚动条像素值定位行；-4 让首行从 y=4 开始，无需改变行高或绘制坐标。
    int maximum = bar->maximum() + kScrollPadding;
    if (bar->maximum() == 0) {
        // Qt 会把负的最大值截为 0；内容接近铺满视口时，必须还原实际高度再加留白。
        int contentHeight = 0;
        const QPoint firstRowPosition(columnViewportPosition(0), -bar->value());
        if (QTreeView::indexAt(firstRowPosition).isValid()) {
            contentHeight = QTreeView::viewportSizeHint().height() + bar->value();
            if (!header()->isHidden()) {
                contentHeight -= header()->height();
            }
        }
        maximum = qMax(-kScrollPadding, contentHeight - viewport()->height() + kScrollPadding);
    }
    bar->setRange(-kScrollPadding, maximum);
}

void TreeView::scrollTo(const QModelIndex &index, ScrollHint hint) {
    QTreeView::scrollTo(index, hint);
    if (!index.isValid() || !uniformRowHeights() || verticalScrollMode() != ScrollPerPixel) {
        return;
    }
    const auto rect = QTreeView::visualRect(index);
    if (rect.isEmpty()) {
        return;
    }

    auto *bar = verticalScrollBar();
    // Qt 的定位只考虑条目高度；首尾条目还需要把条目外的留白一起滚入视口。
    if ((hint == EnsureVisible || hint == PositionAtTop) && rect.top() + bar->value() == 0 &&
        rect.top() < kScrollPadding) {
        bar->setValue(bar->minimum());
    } else if ((hint == EnsureVisible || hint == PositionAtBottom) && !indexBelow(index).isValid() &&
               rect.bottom() >= viewport()->height() - kScrollPadding) {
        bar->setValue(bar->maximum());
    }
}

void TreeView::TreeItemDelegate::initStyleOption(QStyleOptionViewItem *option, const QModelIndex &index) const {
    QStyledItemDelegate::initStyleOption(option, index);
    option->state &= ~QStyle::State_HasFocus;
    // 显式指定的状态色继续保留，其余选中文字遵循主题的 HighlightedText。
    if (index.data(Qt::ForegroundRole).isValid()) {
        for (const auto group : {QPalette::Active, QPalette::Inactive, QPalette::Disabled}) {
            option->palette.setBrush(group, QPalette::HighlightedText, option->palette.brush(group, QPalette::Text));
        }
    }
}

QSize TreeView::TreeItemDelegate::sizeHint(const QStyleOptionViewItem &option, const QModelIndex &index) const {
    auto size = QStyledItemDelegate::sizeHint(option, index);
    const auto padding = itemViewRowPadding(qobject_cast<const QAbstractItemView *>(parent()), index);
    size.rheight() += padding.top() + padding.bottom();
    return size;
}

QRect TreeView::visualRect(const QModelIndex &index) const {
    return QTreeView::visualRect(index).marginsRemoved(itemViewRowPadding(this, index));
}

#if QT_VERSION < QT_VERSION_CHECK(6, 0, 0)
QStyleOptionViewItem TreeView::viewOptions() const {
    QStyleOptionViewItem option = QTreeView::viewOptions();
    if (!hasFocus()) {
        // drawBranches 会重新取 viewOptions，箭头背景也要使用非焦点选中色。
        option.palette.setCurrentColorGroup(QPalette::Inactive);
    }
    return option;
}
#endif

void TreeView::drawRow(QPainter *painter, const QStyleOptionViewItem &options, const QModelIndex &index) const {
    QStyleOptionViewItem drawOption = options;
    drawOption.rect = drawOption.rect.marginsRemoved(itemViewRowPadding(this, index));
    if (!hasFocus()) {
        drawOption.state &= ~QStyle::State_Active;
    }

    if (selectionModel() && selectionModel()->isSelected(index)) {
        // 内容末端进入视口后，选中背景延伸到视口边缘；超出时，圆角仍随内容滚动。
        const int itemIndentation = columnWidth(index.column()) - visualRect(index).width();
        const int offset = header()->offset();
        const int width = qMax(viewport()->width() + offset, itemIndentation + sizeHintForIndex(index).width());
        const int x = isRightToLeft() ? viewport()->width() - width + offset : -offset;
        const QRectF rect(x + 4, drawOption.rect.y(), qMax(0, width - 8), drawOption.rect.height());
        constexpr qreal radius = 4;
        auto backgroundRect = rect;
        // Extend joined edges beyond the row's clip so only the group's outer corners are rounded.
        if (selectionModel()->isSelected(indexAbove(index))) {
            backgroundRect.adjust(0, -radius, 0, 0);
        }
        if (selectionModel()->isSelected(indexBelow(index))) {
            backgroundRect.adjust(0, 0, 0, radius);
        }

        QPalette::ColorGroup colorGroup = QPalette::Active;
        if (!(drawOption.state & QStyle::State_Enabled)) {
            colorGroup = QPalette::Disabled;
        } else if (!(drawOption.state & QStyle::State_Active)) {
            colorGroup = QPalette::Inactive;
        }

        painter->save();
        painter->setRenderHint(QPainter::Antialiasing);
        painter->setClipRect(rect, Qt::IntersectClip);
        painter->setPen(Qt::NoPen);
        painter->setBrush(drawOption.palette.brush(colorGroup, QPalette::Highlight));
        // 与 app.qss 中 QPushButton / QToolButton 的 border-radius: 4px 一致。
        painter->drawRoundedRect(backgroundRect, radius, radius, Qt::AbsoluteSize);
        painter->restore();
    }

    // 文字、图标、展开箭头等继续由 Qt 绘制。
    QTreeView::drawRow(painter, drawOption, index);
}

void TreeView::drawBranches(QPainter *painter, const QRect &rect, const QModelIndex &index) const {
    // 箭头向内容靠近，同时增加与选中背景外缘的距离；仍在原展开点击区域内。
    painter->save();
    painter->translate(isRightToLeft() ? -4 : 4, 0);
    QTreeView::drawBranches(painter, rect, index);
    painter->restore();
}

QRegion TreeView::visualRegionForSelection(const QItemSelection &selection) const {
    const QRegion originalRegion = QTreeView::visualRegionForSelection(selection);

    QRegion fullRowRegion;
    const QRect viewportRect = viewport()->rect();

    // Include adjacent rows because changing the selection also changes their joined corners.
    for (const QRect &rect : originalRegion) {
        const auto above = indexAt(QPoint(viewportRect.center().x(), rect.top() - 1));
        const auto below = indexAt(QPoint(viewportRect.center().x(), rect.bottom() + 1));
        const int top = above.isValid() ? QTreeView::visualRect(above).top() : rect.top();
        const int bottom = below.isValid() ? QTreeView::visualRect(below).bottom() : rect.bottom();
        const QRect rowRect(0, top, viewportRect.width(), bottom - top + 1);
        fullRowRegion |= rowRect.intersected(viewportRect);
    }

    return fullRowRegion;
}

QModelIndex TreeView::indexAt(const QPoint &point) const {
    const auto index = QTreeView::indexAt(point);
    const auto rect = visualRect(index);
    if (index.isValid() && (point.y() < rect.top() || point.y() > rect.bottom())) {
        return {};
    }
    // 原本能命中的位置继续使用 Qt 的结果。
    if (index.isValid() || !viewport()->rect().contains(point)) {
        return index;
    }

    const int width = columnWidth(0);
    if (isColumnHidden(0) || width <= 0) {
        return {};
    }

    // 保留鼠标的纵坐标，将横坐标映射到第 0 列内部。
    // 使用列的视口位置，可以兼容横向滚动。
    const int x = columnViewportPosition(0) + width / 2;
    return QTreeView::indexAt(QPoint(x, point.y()));
}

void TreeView::wheelEvent(QWheelEvent *event) {
#ifdef Q_OS_WIN
    // Windows 的普通滚轮不会自动把 Shift 转成横向滚动。
    if (event->modifiers().testFlag(Qt::ShiftModifier) && !event->modifiers().testFlag(Qt::ControlModifier)) {
        auto *bar = horizontalScrollBar();
        const QPoint pixelDelta = event->pixelDelta();
        if (!pixelDelta.isNull()) {
            const int distance = pixelDelta.x() != 0 ? pixelDelta.x() : pixelDelta.y();
            bar->setValue(bar->value() - distance);
            event->accept();
        } else {
            const QPoint angleDelta = event->angleDelta();
            const int distance = angleDelta.x() != 0 ? angleDelta.x() : angleDelta.y();
            // 去掉 Shift，避免 Qt 将它解释为翻页；由滚动条保留步长和小角度累计。
            QWheelEvent horizontalEvent(event->position(), event->globalPosition(), {}, QPoint(distance, 0),
                                        event->buttons(), event->modifiers() & ~Qt::ShiftModifier, event->phase(),
                                        event->inverted(), event->source());
            QCoreApplication::sendEvent(bar, &horizontalEvent);
            event->setAccepted(horizontalEvent.isAccepted());
        }
        return;
    }
#endif

    const auto phase = event->phase();

    if (phase == Qt::ScrollBegin) {
        m_scroll_axis = ScrollAxis::Vertical;
        m_direction_x = 0;
        m_direction_y = 0;
        m_pending_cross_delta = 0;
    }

    // 普通鼠标滚轮及 Ctrl 操作使用 Qt 默认处理。
    if (phase == Qt::NoScrollPhase || event->modifiers().testFlag(Qt::ControlModifier)) {
        QTreeView::wheelEvent(event);
        return;
    }

    const QPoint delta = event->pixelDelta();

    // 零位移事件不参与方向判断，也不衰减历史。
    if (delta.isNull()) {
        event->accept();
        return;
    }

    int distance = m_scroll_axis == ScrollAxis::Vertical ? delta.y() : delta.x();

    if (phase != Qt::ScrollMomentum && phase != Qt::ScrollEnd) {
        constexpr qreal decay = 0.8;
        constexpr qreal switchRatio = 2.0;
        constexpr qreal minimumDifference = 3.0;

        const qreal x = qAbs(delta.x());
        const qreal y = qAbs(delta.y());

        m_direction_x = m_direction_x * decay + x;
        m_direction_y = m_direction_y * decay + y;

        if (m_scroll_axis == ScrollAxis::Vertical) {
            m_pending_cross_delta = x > y ? m_pending_cross_delta + delta.x() : 0;
            if (x > y && m_direction_x >= m_direction_y * switchRatio &&
                m_direction_x - m_direction_y >= minimumDifference) {
                m_scroll_axis = ScrollAxis::Horizontal;
                distance = m_pending_cross_delta;
                m_pending_cross_delta = 0;
            }
        } else {
            m_pending_cross_delta = y > x ? m_pending_cross_delta + delta.y() : 0;
            if (y > x && m_direction_y >= m_direction_x * switchRatio &&
                m_direction_y - m_direction_x >= minimumDifference) {
                m_scroll_axis = ScrollAxis::Vertical;
                distance = m_pending_cross_delta;
                m_pending_cross_delta = 0;
            }
        }
    }

    auto *bar = m_scroll_axis == ScrollAxis::Vertical ? verticalScrollBar() : horizontalScrollBar();
    bar->setValue(bar->value() - distance);

    event->accept();
}

bool TreeView::event(QEvent *event) {
    if (event->type() == QEvent::ShortcutOverride &&
        dynamic_cast<QKeyEvent *>(event)->matches(QKeySequence::SelectAll)) {
        // 优先交给树的内置全选逻辑，避免 macOS Edit 菜单截走 Command+A。
        event->accept();
        return true;
    }
    return QTreeView::event(event);
}

void TreeView::mousePressEvent(QMouseEvent *event) {
    if (QTreeView::indexAt(event->pos()).isValid() && !indexAt(event->pos()).isValid()) {
        event->accept();
        return;
    }
    const bool auto_scroll_enabled = hasAutoScroll();
    setAutoScroll(false);
    QTreeView::mousePressEvent(event);
    setAutoScroll(auto_scroll_enabled);
}
