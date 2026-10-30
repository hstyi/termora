#pragma once
#include <QObject>

class QSshSession : public QObject {
    Q_OBJECT
public:
    explicit QSshSession(QObject *parent = nullptr);
};