package app.termora.transfer

import org.apache.commons.io.IOUtils
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class FileTransfer(
    parentId: String, source: Path, target: Path, private val size: Long,
    private val action: TransferAction,
    priority: Transfer.Priority = Transfer.Priority.Normal,
) : AbstractTransfer(parentId, source, target, false, priority), Closeable {

    private lateinit var input: InputStream
    private lateinit var output: OutputStream

    override suspend fun transfer(bufferSize: Int): Long {

        if (::input.isInitialized.not()) {
            input = source().inputStream(StandardOpenOption.READ)
        }

        if (::output.isInitialized.not()) {
            output = if (action == TransferAction.Overwrite) {
                target().outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            } else {
                target().outputStream(StandardOpenOption.WRITE, StandardOpenOption.APPEND)
            }
        }

        val buffer = ByteArray(bufferSize)
        val len = input.read(buffer)
        if (len <= 0) return 0
        output.write(buffer, 0, len)
        return len.toLong()
    }

    override fun scanning(): Boolean {
        return false
    }

    override fun size(): Long {
        return size
    }

    override fun close() {
        if (::input.isInitialized) {
            IOUtils.closeQuietly(input)
        }

        if (::output.isInitialized) {
            IOUtils.closeQuietly(output)
        }
    }

}