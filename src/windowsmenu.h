#pragma once

#include <QObject>

class WindowsMenu : public QObject {
    Q_OBJECT

public:
    explicit WindowsMenu(QObject *parent = nullptr);

    bool eventFilter(QObject *watched, QEvent *event) override;
};
