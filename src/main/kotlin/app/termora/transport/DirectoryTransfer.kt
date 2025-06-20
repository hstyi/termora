package app.termora.transport

import java.nio.file.Path
import kotlin.io.path.createDirectories

class DirectoryTransfer(parentId: String, source: Path, target: Path) :
    AbstractTransfer(parentId, source, target, true), TransferScanner {


    @Volatile
    private var scanned = false

    override suspend fun transfer(bufferSize: Int): Long {
        target().createDirectories()
        return 0
    }

    override fun size(): Long {
        return 0
    }

    override fun scanning(): Boolean {
        return scanned.not()
    }

    override fun scanned() {
        scanned = true
    }
}