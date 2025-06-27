package app.termora.plugins.obs

import app.termora.transfer.s3.S3FileSystem
import org.apache.commons.io.IOUtils

/**
 * key: region
 */
class OBSFileSystem(private val clientHandler: OBSClientHandler) :
    S3FileSystem(OBSFileSystemProvider(clientHandler)) {

    override fun close() {
        IOUtils.closeQuietly(clientHandler)
        super.close()
    }
}
