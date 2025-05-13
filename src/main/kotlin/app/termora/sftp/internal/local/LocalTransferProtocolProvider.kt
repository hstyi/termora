package app.termora.sftp.internal.local

import app.termora.Host
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.provider.FileProvider
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider

internal class LocalTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { LocalTransferProtocolProvider() }
        private val localFileProvider by lazy { DefaultLocalFileProvider() }
    }

    override fun getFileProvider(): FileProvider {
        return localFileProvider
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