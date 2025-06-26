package app.termora.plugins.cos

import app.termora.transfer.s3.S3FileSystem
import org.apache.commons.io.IOUtils

/**
 * key: region
 */
class COSFileSystem(private val clientHandler: COSClientHandler) :
    S3FileSystem(COSFileSystemProvider(clientHandler)) {

    override fun close() {
        IOUtils.closeQuietly(clientHandler)
        super.close()
    }
}
