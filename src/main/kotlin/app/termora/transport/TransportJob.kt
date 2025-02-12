package app.termora.transport

import java.nio.file.Path

data class TransportJob(
    /**
     * 发起方
     */
    val fileSystemPanel: FileSystemPanel,
    /**
     * 发起方工作目录
     */
    val workdir: Path,
    /**
     * 要传输的文件是否是文件夹
     */
    val isDirectory: Boolean,
    /**
     * 要传输的文件/文件夹
     */
    val path: Path,

    /**
     * 监听
     */
    val listener: TransportListener? = null
)