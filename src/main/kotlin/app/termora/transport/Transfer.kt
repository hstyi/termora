package app.termora.transport

import org.apache.commons.net.io.Util
import java.nio.file.Path

interface Transfer {


    /**
     * 每调用一次，传输一次
     *
     */
    suspend fun transfer(bufferSize: Int = Util.DEFAULT_COPY_BUFFER_SIZE): Long

    /**
     * 源
     */
    fun source(): Path

    /**
     * 目标
     */
    fun target(): Path

    fun size(): Long

    /**
     * 是否是文件夹
     */
    fun isDirectory(): Boolean

    /**
     * 如果是文件夹，可能正在扫描中
     */
    fun scanning(): Boolean

    /**
     * 任务 ID
     */
    fun id(): String

    /**
     * 父任务 ID，为空则没有
     */
    fun parentId(): String

}