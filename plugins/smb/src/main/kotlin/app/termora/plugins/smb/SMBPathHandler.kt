package app.termora.plugins.smb

import app.termora.protocol.PathHandler
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.session.Session
import org.apache.commons.io.IOUtils
import java.nio.file.FileSystem
import java.nio.file.Path

class SMBPathHandler(
    private val client: SMBClient,
    private val session: Session,
    fileSystem: FileSystem, path: Path
) : PathHandler(fileSystem, path) {
    override fun dispose() {
        super.dispose()
        session.close()
        IOUtils.closeQuietly(client)
    }
}