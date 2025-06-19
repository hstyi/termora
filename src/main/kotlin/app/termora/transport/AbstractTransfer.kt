package app.termora.transport

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractTransfer(
    private val parentId: String,
    private val source: Path,
    private val target: Path,
    private val isDirectory: Boolean
) : Transfer {

    companion object {
        private val ID = AtomicLong()
    }

    private val id = ID.incrementAndGet().toString()


    override fun source(): Path {
        return source
    }

    override fun target(): Path {
        return target
    }

    override fun isDirectory(): Boolean {
        return isDirectory
    }

    override fun parentId(): String {
        return parentId
    }

    override fun id(): String {
        return id
    }

    override fun scanning(): Boolean {
        return false
    }
}