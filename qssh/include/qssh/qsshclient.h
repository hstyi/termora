#pragma once
#include <QObject>

class QSshClient : public QObject {
    Q_OBJECT
public:
    explicit QSshClient(QObject *parent = nullptr);
};