#pragma once

#include <QFileDevice>

/** Qt 权限与远端 POSIX mode 的转换；不能直接强制转换两套数值。 */
namespace qssh_sftp {
    /** Qt 的 User* 输入按 Owner* 的别名处理，不推测远端登录用户的有效访问权。 */
    inline bool validPermissions(QFileDevice::Permissions permissions) {
        const auto supported = QFileDevice::ReadOwner | QFileDevice::WriteOwner | QFileDevice::ExeOwner |
                               QFileDevice::ReadUser | QFileDevice::WriteUser | QFileDevice::ExeUser |
                               QFileDevice::ReadGroup | QFileDevice::WriteGroup | QFileDevice::ExeGroup |
                               QFileDevice::ReadOther | QFileDevice::WriteOther | QFileDevice::ExeOther;
        return !(static_cast<int>(permissions) & ~static_cast<int>(supported));
    }

    /** 把 permissions 的普通 rwx 位转换为 SFTP 使用的 POSIX mode，不包含文件类型及特殊位。 */
    inline quint32 permissionsToMode(QFileDevice::Permissions permissions) {
        quint32 mode = 0;
        if (permissions & (QFileDevice::ReadOwner | QFileDevice::ReadUser)) {
            mode |= 0400;
        }
        if (permissions & (QFileDevice::WriteOwner | QFileDevice::WriteUser)) {
            mode |= 0200;
        }
        if (permissions & (QFileDevice::ExeOwner | QFileDevice::ExeUser)) {
            mode |= 0100;
        }
        if (permissions.testFlag(QFileDevice::ReadGroup)) {
            mode |= 0040;
        }
        if (permissions.testFlag(QFileDevice::WriteGroup)) {
            mode |= 0020;
        }
        if (permissions.testFlag(QFileDevice::ExeGroup)) {
            mode |= 0010;
        }
        if (permissions.testFlag(QFileDevice::ReadOther)) {
            mode |= 0004;
        }
        if (permissions.testFlag(QFileDevice::WriteOther)) {
            mode |= 0002;
        }
        if (permissions.testFlag(QFileDevice::ExeOther)) {
            mode |= 0001;
        }
        return mode;
    }

    /** 把 mode 解码成 Owner/Group/Other 权限；原始特殊位和类型由 FileInfo.mode 单独保存。 */
    inline QFileDevice::Permissions permissionsFromMode(quint32 mode) {
        QFileDevice::Permissions permissions;
        if (mode & 0400) {
            permissions |= QFileDevice::ReadOwner;
        }
        if (mode & 0200) {
            permissions |= QFileDevice::WriteOwner;
        }
        if (mode & 0100) {
            permissions |= QFileDevice::ExeOwner;
        }
        if (mode & 0040) {
            permissions |= QFileDevice::ReadGroup;
        }
        if (mode & 0020) {
            permissions |= QFileDevice::WriteGroup;
        }
        if (mode & 0010) {
            permissions |= QFileDevice::ExeGroup;
        }
        if (mode & 0004) {
            permissions |= QFileDevice::ReadOther;
        }
        if (mode & 0002) {
            permissions |= QFileDevice::WriteOther;
        }
        if (mode & 0001) {
            permissions |= QFileDevice::ExeOther;
        }
        return permissions;
    }
} // namespace qssh_sftp
