package app.termora.transfer

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractTransfer(
    private val parentId: String,
    private val source: Path,
    private val target: Path,
    private val isDirectory: Boolean,
    private val priority: Transfer.Priority = Transfer.Priority.Normal,
) : Transfer {

    companion object {
        private val ID = AtomicLong()
    }

    private val id = ID.incrementAndGet().toString()
    private val handler: TransferHandler = object : TransferHandler {
        override fun isDisposed(): Boolean {
            return source.fileSystem.isOpen.not() || target.fileSystem.isOpen.not()
        }
    }

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

    override fun handler(): TransferHandler {
        return handler
    }

    final override fun priority(): Transfer.Priority {
        return priority
    }
}