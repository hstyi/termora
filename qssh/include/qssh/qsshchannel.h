#pragma once
#include <QObject>

class QSshChannel : public QObject {
    Q_OBJECT
public:
    explicit QSshChannel(QObject *parent = nullptr);
};