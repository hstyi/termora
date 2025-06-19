package app.termora.transport

import java.nio.file.Path
import kotlin.io.path.createDirectories

class DirectoryTransfer(parentId: String, source: Path, target: Path) :
    AbstractTransfer(parentId, source, target, true) {


    @Volatile
    private var scanned = false

    override suspend fun transfer(bufferSize: Int): Int {
        target().createDirectories()
        return 0
    }

    override fun size(): Long {
        return 0
    }

    override fun scanning(): Boolean {
        return scanned.not()
    }

    fun scanned() {
        scanned = true
    }
}