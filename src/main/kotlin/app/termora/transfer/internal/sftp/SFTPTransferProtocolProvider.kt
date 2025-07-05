package app.termora.transfer.internal.sftp

import app.termora.plugin.internal.ssh.SshClients
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.sftp.client.SftpClientFactory

internal class SFTPTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { SFTPTransferProtocolProvider() }
        const val PROTOCOL = "sftp"
    }

    override fun isTransient(): Boolean {
        return true
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        var client: SshClient? = null
        var session: ClientSession? = null
        try {
            val owner = requester.owner
            client = if (owner == null) SshClients.openClient(requester.host)
            else SshClients.openClient(requester.host, owner)
            session = SshClients.openSession(requester.host, client)
            val fileSystem = SftpClientFactory.instance().createSftpFileSystem(session)

            val host = requester.host
            var path = fileSystem.defaultDir
            val defaultDirectory = host.options.sftpDefaultDirectory
            if (StringUtils.isNotBlank(defaultDirectory)) {
                path = fileSystem.getPath(defaultDirectory)
            }
            return SFTPPathHandler(fileSystem, path, client, session)
        } catch (e: Exception) {
            IOUtils.closeQuietly(session)
            IOUtils.closeQuietly(client)
            throw e
        }
    }
}