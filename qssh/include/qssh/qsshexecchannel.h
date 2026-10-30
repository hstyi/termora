#pragma once

#include <qssh/qsshchannel.h>

/** 执行一条服务器解释的命令；不申请 PTY，stdout 和 stderr 分开保存。 */
class QSshExecChannel : public QSshChannel {
    Q_OBJECT
public:
    /** 请求执行 command，按 UTF-8 原样发送；服务器决定命令解释方式。 */
    void open(const QString &command);

private:
    /** 由 session 创建并管理生命周期。 */
    explicit QSshExecChannel(QSshSession *session);
    friend class QSshSession;
};
