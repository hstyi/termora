#pragma once

#include <QFileDevice>

/** 打开策略；访问权限和创建行为分开指定，不隐式截断已有文件。 */
struct QVirtualOpenOptions {
    /** 句柄访问方向。 */
    enum Access {
        ReadOnly,  ///< 仅允许读取，必须打开已有路径。
        WriteOnly, ///< 仅允许写入，创建行为由 creation 决定。
        ReadWrite  ///< 同时允许读写，不隐式截断已有内容。
    };

    /** 路径存在性及初始内容策略。 */
    enum Creation {
        OpenExisting, ///< 必须已存在，保留原内容。
        CreateNew,    ///< 必须不存在，后端使用排他创建。
        OpenOrCreate, ///< 不存在则创建，存在则保留原内容。
        Replace       ///< 不存在则创建，存在则截断或按 atomicReplace 暂存替换。
    };

    Access access = ReadOnly;         ///< 只读、只写或读写；默认只读。
    Creation creation = OpenExisting; ///< Replace 创建或截断，CreateNew 必须排他创建。
    bool append = false;              ///< 写入追加到末尾；writeAt 的 offset 不决定追加位置。
    bool atomicReplace = false;       ///< 仅支持 WriteOnly + Replace；关闭成功时发布完整新内容，不支持则失败。
    QFileDevice::Permissions permissions = QFileDevice::ReadOwner | QFileDevice::WriteOwner | QFileDevice::ReadGroup |
                                           QFileDevice::ReadOther; ///< 创建时的普通 rwx 权限。
};

/** 删除策略；默认删除文件、链接或空目录。 */
struct QVirtualRemoveOptions {
    bool recursive = false;     ///< 显式递归目录；始终不跟随符号链接，根入口禁止删除。
    bool ignoreMissing = false; ///< 目标已不存在时视为成功，其他错误仍报告。
};

/** 同一命名空间内的重命名策略，不隐式退化成跨文件系统复制。 */
struct QVirtualRenameOptions {
    bool overwrite = false;     ///< 是否允许替换目标；后端无法保证此策略时报告 Unsupported。
    bool requireAtomic = false; ///< 必须使用原子重命名，否则报告 Unsupported。
};

/** 单文件复制策略；递归目录复制应由上层分别创建传输任务。 */
struct QVirtualTransferOptions {
    bool overwrite = false;              ///< 目标存在时默认失败；不先删除目标以模拟覆盖。
    bool atomicReplace = false;          ///< 覆盖时要求完整内容提交后替换目标，不支持则失败。
    qint64 bufferSize = 4 * 1024 * 1024; ///< 预读、在途和乱序结果的合计预算，64 KiB..64 MiB。
    int idleTimeoutMs = 30000;           ///< 没有内容进展的时限，必须大于零。
};
