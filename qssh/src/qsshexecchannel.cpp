#include "qsshchannel_p.h"

#include <qssh/qsshexecchannel.h>

QSshExecChannel::QSshExecChannel(QSshSession *session) : QSshChannel(session, false) {}

void QSshExecChannel::open(const QString &command) {
    Q_D(QSshChannel);
    d->open(command);
}
