package app.termora.transfer.s3

import org.apache.commons.io.IOUtils
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel

open class S3ReadSeekableByteChannel(input: InputStream, private val size: Long) : SeekableByteChannel {
    private val channel = Channels.newChannel(input)
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
        return size
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
