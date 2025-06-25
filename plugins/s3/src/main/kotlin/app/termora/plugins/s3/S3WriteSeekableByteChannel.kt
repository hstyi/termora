package app.termora.plugins.s3

import org.apache.commons.io.IOUtils
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.channels.WritableByteChannel

class S3WriteSeekableByteChannel(
    private val channel: WritableByteChannel,
) : SeekableByteChannel {


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
