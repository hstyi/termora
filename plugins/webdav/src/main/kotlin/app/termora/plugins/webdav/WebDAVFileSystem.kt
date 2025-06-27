package app.termora.plugins.webdav

import app.termora.transfer.s3.S3FileSystem
import app.termora.transfer.s3.S3Path
import com.github.sardine.Sardine

class WebDAVFileSystem(
    private val sardine: Sardine, endpoint: String,
    authorization: String,
) :
    S3FileSystem(WebDAVFileSystemProvider(sardine, endpoint, authorization)) {

    override fun create(root: String?, names: List<String>): S3Path {
        val path = WebDAVPath(this, root, names)
        if (names.isEmpty()) {
            path.attributes = path.attributes.copy(directory = true)
        }
        return path
    }

    override fun close() {
        sardine.shutdown()
        super.close()
    }
}
