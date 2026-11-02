#pragma once

#include <QWidget>

class MacScrollStyle {
public:
    enum class Direction {
        Normal,
        Reverse
    };

    // Normal: vertical on the right, horizontal at the bottom.
    // Reverse: vertical on the left, horizontal at the top.
    static void use(QWidget *widget, Direction vertical = Direction::Normal, Direction horizontal = Direction::Normal);
};
