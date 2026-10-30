#pragma once

#include <QProcess>
#include <QTemporaryDir>
#include <functional>
#include <gtest/gtest.h>
#include <qssh/qsshsession.h>

namespace qssh_test {
    /** 在 milliseconds 内驱动 Qt 事件循环直到 condition 成立，不依赖固定等待时长。 */
    bool waitUntil(const std::function<bool()> &condition, int milliseconds = 10000);
} // namespace qssh_test

/** 每个真实协议用例拥有独立回环 SSH 服务、测试密钥及 known_hosts，不接触用户 SSH 配置。 */
class QSshIntegration : public ::testing::Test {
protected:
    /** 检查 Python/Paramiko 依赖并启动隔离服务，依赖缺失时明确跳过。 */
    void SetUp() override;
    /** 输出失败诊断并终止隔离服务，无论断言是否通过都回收进程。 */
    void TearDown() override;
    /** 为 user 创建指向本用例服务的连接配置，known_hosts 位于隔离目录。 */
    QSshSession::Options options(const QString &user = QStringLiteral("password")) const;
    /** 为 session 安装测试主机确认处理器，仅接受本次连接的密钥。 */
    void acceptHost(QSshSession *session);
    /** 以 user 配置连接 session，并等待完整认证成功。 */
    void login(QSshSession *session, const QString &user = QStringLiteral("password"));

    QTemporaryDir directory; ///< 本用例独占的主机密钥、用户私钥和 known_hosts 目录。
    QProcess server;         ///< 本用例独占的 SSH 测试服务进程。
    quint16 port = 0;        ///< 隔离服务实际监听的临时 TCP 端口。
};
