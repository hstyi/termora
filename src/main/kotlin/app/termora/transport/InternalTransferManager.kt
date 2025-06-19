package app.termora.transport

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

interface InternalTransferManager {
    /**
     * 是否允许传输，添加任务之前请调用
     */
    fun canTransfer(paths: List<Path>): Boolean

    /**
     * 添加任务，如果是文件夹会递归查询子然后传递
     */
    fun addTransfer(path: Path, isDirectory: Boolean): CompletableFuture<Unit>
}