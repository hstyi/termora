package app.termora.transport

import org.apache.commons.io.IOUtils
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class FileTransfer(parentId: String, source: Path, target: Path, private val size: Long) :
    AbstractTransfer(parentId, source, target, false), Closeable {

    val input by lazy { source().inputStream(StandardOpenOption.READ) }
    val output by lazy { target().outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING) }

    override suspend fun transfer(bufferSize: Int): Int {
        val buffer = ByteArray(bufferSize)
        val len = input.read(buffer)
        if (len <= 0) return 0
        try {
            output.write(buffer, 0, len)
        } catch (e: Exception) {
            println(target().parent.absolutePathString())
            println(target().parent.exists())
        throw e
        }
        return len
    }

    override fun scanning(): Boolean {
        return false
    }

    override fun size(): Long {
        return size
    }

    override fun close() {
        IOUtils.closeQuietly(input)
        IOUtils.closeQuietly(output)
    }

}