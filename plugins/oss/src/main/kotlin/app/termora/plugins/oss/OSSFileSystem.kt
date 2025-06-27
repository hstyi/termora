package app.termora.plugins.oss

import app.termora.transfer.s3.S3FileSystem
import org.apache.commons.io.IOUtils

/**
 * key: region
 */
class OSSFileSystem(private val clientHandler: OSSClientHandler) :
    S3FileSystem(OSSFileSystemProvider(clientHandler)) {

    override fun close() {
        IOUtils.closeQuietly(clientHandler)
        super.close()
    }
}
