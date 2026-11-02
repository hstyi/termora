#pragma once

#include <QIcon>
#include <QString>

struct Presentation {
    QString text;
    QString toolTip;
    QIcon icon;

    bool enabled = true;
    bool visible = true;
    bool checked = false;
};
