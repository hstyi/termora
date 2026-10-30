#pragma once

#include <QSize>
#include <qssh/qsshchannel.h>

/** 申请 PTY 并启动交互 shell，支持活动终端尺寸调整。 */
class QSshShellChannel : public QSshChannel {
    Q_OBJECT
public:
    /** 使用 terminalType 申请终端并启动 shell，初始网格默认 80×24。 */
    void open(const QString &terminalType = QStringLiteral("xterm-256color"));
    /** 保存 size 并异步调整 PTY，每维范围 1..32767；连续请求合并为最新尺寸。 */
    bool setTerminalSize(const QSize &size);
    /** 返回期望字符网格尺寸，width 为列数，height 为行数。 */
    QSize terminalSize() const;

private:
    /** 由 session 创建并管理生命周期。 */
    explicit QSshShellChannel(QSshSession *session);
    friend class QSshSession;
};
