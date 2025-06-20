package app.termora.transport

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

class DeleteTransfer(parentId: String, path: Path, isDirectory: Boolean, private val size: Long) :
    AbstractTransfer(parentId, path, path, isDirectory), TransferScanner {

    private var scanned = false
    private var deleted = false

    override suspend fun transfer(bufferSize: Int): Long {
        if (deleted) return 0
        Files.deleteIfExists(source())
        deleted = true
        return this.size()
    }

    override fun scanning(): Boolean {
        return if (isDirectory()) scanned.not() else false
    }

    override fun scanned() {
        scanned = true
    }

    override fun size(): Long {
        return max(size, 1)
    }

}