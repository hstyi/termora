package app.termora.plugins.s3

import io.minio.StatObjectResponse
import org.apache.commons.io.IOUtils
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel

class S3ReadSeekableByteChannel(
    private val channel: ReadableByteChannel,
    private val stat: StatObjectResponse
) : SeekableByteChannel {

    private var position: Long = 0

    override fun read(dst: ByteBuffer): Int {
        val bytesRead = channel.read(dst)
        if (bytesRead > 0) {
            position += bytesRead
        }
        return bytesRead
    }

    override fun write(src: ByteBuffer): Int {
        throw UnsupportedOperationException("Read-only channel")
    }

    override fun position(): Long {
        return position
    }

    override fun position(newPosition: Long): SeekableByteChannel {
        throw UnsupportedOperationException("Seek not supported in streaming read")
    }

    override fun size(): Long {
        return stat.size()
    }

    override fun truncate(size: Long): SeekableByteChannel {
        throw UnsupportedOperationException("Read-only channel")
    }

    override fun isOpen(): Boolean {
        return channel.isOpen
    }

    override fun close() {
        IOUtils.closeQuietly(channel)
    }
}
