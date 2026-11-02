#pragma once

#include <QMenu>

class Menu : public QMenu {
public:
    using QMenu::addMenu;
    using QMenu::QMenu;

    Menu *addMenu(const QString &title);

protected:
    void paintEvent(QPaintEvent *event) override;
};
