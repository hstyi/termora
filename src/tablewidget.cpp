#include "tablewidget.h"

#include "macscrollstyle.h"

TableWidget::TableWidget(QWidget *parent) : QTableWidget(parent) {
    setItemDelegate(new TableItemDelegate(this));
    auto headerFont = font();
    headerFont.setPointSizeF(headerFont.pointSizeF());
    horizontalHeader()->setFont(headerFont);
    horizontalHeader()->setSectionResizeMode(QHeaderView::Stretch);
    horizontalHeader()->setFixedHeight(22);
    horizontalHeader()->setDefaultAlignment(Qt::AlignVCenter | Qt::AlignLeft);
    horizontalHeader()->setHighlightSections(false);
    verticalHeader()->setMinimumSectionSize(20);
    verticalHeader()->setDefaultSectionSize(20);
    verticalHeader()->setSectionResizeMode(QHeaderView::Fixed);
    verticalHeader()->hide();
    setSelectionBehavior(SelectRows);
    setSelectionMode(ExtendedSelection);
    setEditTriggers(NoEditTriggers);
    setHorizontalScrollMode(ScrollPerPixel);
    setVerticalScrollMode(ScrollPerPixel);
    MacScrollStyle::use(this);
}
