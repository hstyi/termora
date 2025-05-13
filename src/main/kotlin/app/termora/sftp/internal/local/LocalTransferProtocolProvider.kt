package app.termora.sftp.internal.local

import app.termora.Host
import app.termora.protocol.TransferProtocolProvider
import app.termora.vfs2.sftp.MySftpFileProvider
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.provider.FileProvider

class LocalTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { LocalTransferProtocolProvider() }
    }

    override fun getFileProvider(): FileProvider {
        return MySftpFileProvider.instance
    }

    override fun getRootFileObject(host: Host): FileObject {
        TODO("Not yet implemented")
    }

    override fun isTransient(): Boolean {
        return true
    }

    override fun getProtocol(): String {
        return "local"
    }
}