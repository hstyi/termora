#pragma once

#include <QDateTime>
#include <QFileDevice>
#include <QList>
#include <QMetaType>
#include <QString>

/**
 * 文件或目录的属性快照，读取字段不会访问后端，也不会自动刷新。
 * type 为 Unknown 表示类型未知；其余可选字段是否有效由 attributes 标记，不能用零值判断。
 */
class QVirtualFileInfo {
    Q_GADGET
public:
    /** 路径本身的类型；符号链接不会被替换成目标类型。 */
    enum Type {
        Unknown,      ///< 后端未报告或无法确定类型。
        File,         ///< 普通文件。
        Directory,    ///< 目录。
        SymbolicLink, ///< 符号链接，目标可以不存在。
        Other         ///< 已知为其他类型，例如设备、套接字或命名管道。
    };
    Q_ENUM(Type)

    /** 可选属性的有效性位图；未设置的字段不得用于业务判断。 */
    enum Attribute {
        Size = 0x01,         ///< size 有效，包括已知大小为零的情况。
        ModifiedTime = 0x02, ///< lastModified 有效。
        Permissions = 0x04   ///< permissions 有效。
    };
    Q_DECLARE_FLAGS(Attributes, Attribute)
    Q_FLAG(Attributes)

    QString path;                         ///< 所属文件系统内的路径标识，不能脱离文件系统比较身份。
    QString name;                         ///< 文件名；根条目使用根路径。
    QString displayName;                  ///< 适合界面显示的名称。
    Type type = Unknown;                  ///< 路径类型；与可选属性位图独立。
    bool isRoot = false;                  ///< 是否为文件系统的根入口，不代表该入口当前可访问。
    Attributes attributes;                ///< 后端实际报告的可选属性；默认均未知。
    quint64 size = 0;                     ///< 大小，单位字节；仅在 Size 置位时有效。
    QDateTime lastModified;               ///< 最近修改时间；仅在 ModifiedTime 置位时有效。
    QFileDevice::Permissions permissions; ///< Qt 权限位；仅在 Permissions 置位时有效，不保证表示当前用户的有效权限。
};
Q_DECLARE_OPERATORS_FOR_FLAGS(QVirtualFileInfo::Attributes)
Q_DECLARE_METATYPE(QVirtualFileInfo)
Q_DECLARE_METATYPE(QList<QVirtualFileInfo>)
