package app.termora.transfer

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.math.max

class ChangePermissionTransfer(
    parentId: String, path: Path, val permissions: Set<PosixFilePermission>,
    isDirectory: Boolean, private val size: Long,
) : AbstractTransfer(parentId, path, path, isDirectory, Transfer.Priority.Normal), TransferScanner {

    private var changed = false
    private var scanned = false

    override suspend fun transfer(bufferSize: Int): Long {
        if (changed) return 0
        Files.setPosixFilePermissions(source(), permissions)
        changed = true
        return size()
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