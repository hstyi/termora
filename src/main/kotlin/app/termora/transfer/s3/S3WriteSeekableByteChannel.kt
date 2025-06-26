package app.termora.transfer.s3

import org.apache.commons.io.IOUtils
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel

open class S3WriteSeekableByteChannel(output: OutputStream) : SeekableByteChannel {
    private val channel = Channels.newChannel(output)

    override fun read(dst: ByteBuffer): Int {
        throw UnsupportedOperationException("read not supported")
    }

    override fun write(src: ByteBuffer): Int {
        return channel.write(src)
    }

    override fun position(): Long {
        throw UnsupportedOperationException("position not supported")
    }

    override fun position(newPosition: Long): SeekableByteChannel {
        throw UnsupportedOperationException("position not supported")
    }

    override fun size(): Long {
        throw UnsupportedOperationException("size not supported")
    }

    override fun truncate(size: Long): SeekableByteChannel {
        throw UnsupportedOperationException("truncate not supported")
    }

    override fun isOpen(): Boolean {
        return channel.isOpen
    }

    override fun close() {
        IOUtils.closeQuietly(channel)
    }
}
