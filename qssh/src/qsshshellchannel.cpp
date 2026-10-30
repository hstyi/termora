#include "qsshchannel_p.h"
#include "qsshsession_p.h"

#include <QThread>
#include <qssh/qsshshellchannel.h>

QSshShellChannel::QSshShellChannel(QSshSession *session) : QSshChannel(session, true) {}

void QSshShellChannel::open(const QString &terminalType) {
    Q_D(QSshChannel);
    d->open(terminalType);
}

bool QSshShellChannel::setTerminalSize(const QSize &size) {
    Q_ASSERT(thread() == QThread::currentThread());
    Q_D(QSshChannel);
    if (size.width() < 1 || size.height() < 1 || size.width() > 32767 || size.height() > 32767) {
        return false;
    }
    d->frontSize = size;
    d->submit([d, size] {
        d->requestedSize = size;
        d->session->schedule();
    });
    return true;
}

QSize QSshShellChannel::terminalSize() const {
    Q_D(const QSshChannel);
    return d->frontSize;
}
