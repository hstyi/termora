package app.termora.transport

import org.apache.commons.io.IOUtils
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class FileTransfer(parentId: String, source: Path, target: Path, private val size: Long) :
    AbstractTransfer(parentId, source, target, false), Closeable {

    private lateinit var input: InputStream
    private lateinit var output: OutputStream

    override suspend fun transfer(bufferSize: Int): Int {
        if (::input.isInitialized.not()) {
            input = source().inputStream(StandardOpenOption.READ)
        }

        if (::output.isInitialized.not()) {
            output = target().outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }

        val buffer = ByteArray(bufferSize)
        val len = input.read(buffer)
        if (len <= 0) return 0
        output.write(buffer, 0, len)
        return len
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