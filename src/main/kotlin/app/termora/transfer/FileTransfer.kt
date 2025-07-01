package app.termora.transfer

import app.termora.database.DatabaseManager
import org.apache.commons.io.IOUtils
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.apache.sshd.sftp.common.SftpConstants
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class FileTransfer(
    parentId: String, source: Path, target: Path, private val size: Long,
    private val action: TransferAction,
    priority: Transfer.Priority = Transfer.Priority.Normal,
) : AbstractTransfer(parentId, source, target, false, priority), Closeable {
    companion object {
        private val log = LoggerFactory.getLogger(FileTransfer::class.java)
    }

    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private val isPreserveModificationTime get() = DatabaseManager.getInstance().sftp.preserveModificationTime
    private val closed = AtomicBoolean(false)

    override suspend fun transfer(bufferSize: Int): Long {

        if (closed.get()) throw IllegalStateException("Transfer already closed")

        if (::input.isInitialized.not()) {
            input = source().inputStream(StandardOpenOption.READ)
        }

        if (::output.isInitialized.not()) {
            output = if (action == TransferAction.Overwrite) {
                target().outputStream()
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
        if (closed.compareAndSet(false, true)) {
            if (::input.isInitialized) {
                IOUtils.closeQuietly(input)
            }

            if (::output.isInitialized) {
                IOUtils.closeQuietly(output)
            }

            if (isPreserveModificationTime) {
                runCatching {
                    val time = source().getLastModifiedTime()
                    val fs = target().fileSystem
                    // SFTP 比较特殊
                    if (fs is SftpFileSystem && fs.version == SftpConstants.SFTP_V3) {
                        val view = Files.getFileAttributeView(target(), BasicFileAttributeView::class.java)
                        view.setTimes(time, time, null)
                    } else {
                        Files.setLastModifiedTime(target(), time)
                    }
                }.onFailure {
                    if (log.isWarnEnabled) {
                        log.error(it.message, it)
                    }
                }
            }
        }
    }

}