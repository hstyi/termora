#include "qssh_test_support.h"

#include <QBuffer>
#include <QMap>
#include <QSet>
#include <qssh/qsshclient.h>
#include <qssh/qsshsftpsession.h>

using qssh_test::waitUntil;

/** 对真正远端 mode 验证 Qt 权限映射，尤其检查 Qt 与 POSIX 数值不同、单枚举及 User 别名。 */
TEST_F(QSshIntegration, SftpQtPermissionsAndRawSpecialBits) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    QMap<quint64, quint64> handles;
    QMap<quint64, QSshSftpSession::FileInfo> infos;
    QSet<quint64> completed;
    QObject::connect(sftp, &QSshSftpSession::fileOpened, sftp, [&](quint64 id, quint64 h) { handles[id] = h; });
    QObject::connect(sftp, &QSshSftpSession::fileInfoAvailable, sftp,
                     [&](quint64 id, const auto &info) { infos[id] = info; });
    QObject::connect(sftp, &QSshSftpSession::requestFinished, sftp, [&](quint64 id) { completed.insert(id); });
    QObject::connect(sftp, &QSshSftpSession::requestFailed, sftp,
                     [](quint64, auto, const QString &message) { ADD_FAILURE() << message.toStdString(); });
    const auto opened = sftp->openFile("/qt-permissions", QSshSftpSession::Write | QSshSftpSession::Create,
                                       QFileDevice::ReadOwner);
    ASSERT_TRUE(waitUntil([&] { return handles.contains(opened); }));
    const auto closed = sftp->closeFile(handles[opened]);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(closed); }));
    const auto owner = sftp->stat("/qt-permissions");
    ASSERT_TRUE(waitUntil([&] { return infos.contains(owner); }));
    EXPECT_EQ(infos[owner].mode & 07777, 0400u);
    EXPECT_EQ(infos[owner].permissions, QFileDevice::Permissions(QFileDevice::ReadOwner));
    // 逐项经过真实 SFTP SETSTAT/STAT，避免 Qt 和 POSIX 恰好部分位值相同掩盖转换错误。
    const std::pair<QFileDevice::Permission, quint32> bits[] = {
        {QFileDevice::ReadOwner, 0400}, {QFileDevice::WriteOwner, 0200}, {QFileDevice::ExeOwner, 0100},
        {QFileDevice::ReadGroup, 0040}, {QFileDevice::WriteGroup, 0020}, {QFileDevice::ExeGroup, 0010},
        {QFileDevice::ReadOther, 0004}, {QFileDevice::WriteOther, 0002}, {QFileDevice::ExeOther, 0001}};
    for (const auto &[permission, mode] : bits) {
        const auto chmod = sftp->setPermissions("/qt-permissions", permission);
        ASSERT_TRUE(waitUntil([&] { return completed.contains(chmod); }));
        const auto stat = sftp->stat("/qt-permissions");
        ASSERT_TRUE(waitUntil([&] { return infos.contains(stat); }));
        EXPECT_EQ(infos[stat].mode & 07777, mode);
        EXPECT_EQ(infos[stat].permissions, QFileDevice::Permissions(permission));
    }
    const auto changed = sftp->setPermissions("/qt-permissions",
                                              QFileDevice::ReadUser | QFileDevice::WriteGroup | QFileDevice::ExeOther);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(changed); }));
    const auto mixed = sftp->stat("/qt-permissions");
    ASSERT_TRUE(waitUntil([&] { return infos.contains(mixed); }));
    EXPECT_EQ(infos[mixed].mode & 07777, 0421u);
    EXPECT_EQ(infos[mixed].permissions, QFileDevice::ReadOwner | QFileDevice::WriteGroup | QFileDevice::ExeOther);
    const auto special = sftp->setPermissions("/qt-permissions", quint32(04750));
    ASSERT_TRUE(waitUntil([&] { return completed.contains(special); }));
    const auto raw = sftp->stat("/qt-permissions");
    ASSERT_TRUE(waitUntil([&] { return infos.contains(raw); }));
    EXPECT_EQ(infos[raw].mode & 07777, 04750u);
    EXPECT_TRUE(infos[raw].permissions.testFlag(QFileDevice::ExeOwner));
    const auto invalid = QFileDevice::Permissions(static_cast<QFileDevice::Permission>(0x8000));
    EXPECT_EQ(sftp->setPermissions("/qt-permissions", invalid), 0u);
    EXPECT_EQ(sftp->makeDirectory("/invalid", invalid), 0u);
    EXPECT_EQ(sftp->openFile("/invalid", QSshSftpSession::Write | QSshSftpSession::Create, invalid), 0u);
    const auto directory = sftp->makeDirectory("/qt-directory", QFileDevice::ReadOwner | QFileDevice::WriteOwner |
                                                                    QFileDevice::ExeOwner);
    ASSERT_TRUE(waitUntil([&] { return completed.contains(directory); }));
    const auto dir = sftp->stat("/qt-directory");
    ASSERT_TRUE(waitUntil([&] { return infos.contains(dir); }));
    EXPECT_EQ(infos[dir].mode & 0177777, 040700u);
}

/** 上传选项使用 Qt 权限，并拒绝未知权限位，不把 Qt 数值误发为远端 mode。 */
TEST_F(QSshIntegration, SftpTransferQtCreationPermissions) {
    QSshClient client;
    auto *ssh = client.createSession();
    login(ssh);
    auto *sftp = ssh->createSftpSession();
    sftp->open();
    ASSERT_TRUE(waitUntil([&] { return sftp->state() == QSshSftpSession::Ready; }));
    QBuffer source;
    source.setData("permissions");
    source.open(QIODevice::ReadOnly);
    QSshSftpTransfer::Options options;
    options.permissions = QFileDevice::ReadOwner | QFileDevice::WriteOwner;
    auto *transfer = sftp->upload(&source, "/private-upload", options);
    ASSERT_TRUE(waitUntil([&] { return transfer->state() >= QSshSftpTransfer::Succeeded; }));
    ASSERT_EQ(transfer->state(), QSshSftpTransfer::Succeeded) << transfer->errorString().toStdString();
    bool received = false;
    QSshSftpSession::FileInfo info;
    QObject::connect(sftp, &QSshSftpSession::fileInfoAvailable, sftp, [&](quint64, const auto &value) {
        info = value;
        received = true;
    });
    ASSERT_NE(sftp->stat("/private-upload"), 0u);
    ASSERT_TRUE(waitUntil([&] { return received; }));
    EXPECT_EQ(info.mode & 07777, 0600u);
    EXPECT_EQ(info.permissions, options.permissions);
    options.permissions = QFileDevice::Permissions(static_cast<QFileDevice::Permission>(0x8000));
    auto *invalid = sftp->upload(&source, "/invalid-upload", options);
    ASSERT_TRUE(waitUntil([&] { return invalid->state() >= QSshSftpTransfer::Succeeded; }));
    EXPECT_EQ(invalid->error(), QSshSftpTransfer::InvalidConfiguration);
}
