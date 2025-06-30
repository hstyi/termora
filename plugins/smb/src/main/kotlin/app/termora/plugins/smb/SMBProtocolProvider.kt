package app.termora.plugins.smb

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils

class SMBProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { SMBProtocolProvider() }
        const val PROTOCOL = "SMB"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.windows7
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val client = SMBClient()
        val host = requester.host
        val connection = client.connect(host.host, host.port)
        val session = when (host.username) {
            "Guest" -> connection.authenticate(AuthenticationContext.guest())
            "Anonymous" -> connection.authenticate(AuthenticationContext.anonymous())
            else -> connection.authenticate(
                AuthenticationContext(
                    host.username,
                    host.authentication.password.toCharArray(),
                    null
                )
            )
        }
        val share = session.connectShare(host.options.extras["smb.share"] ?: StringUtils.EMPTY) as DiskShare
        var sftpDefaultDirectory = StringUtils.defaultString(host.options.sftpDefaultDirectory)
        sftpDefaultDirectory = if (sftpDefaultDirectory.isNotBlank()) {
            FilenameUtils.separatorsToUnix(sftpDefaultDirectory)
        } else {
            "/"
        }

        val fs = SMBFileSystem(share, session)
        return SMBPathHandler(client, session, fs, fs.getPath(sftpDefaultDirectory))
    }


}