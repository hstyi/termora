package app.termora.sftp.internal.local

import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequester
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.vfs2.VFS
import org.apache.commons.vfs2.provider.FileProvider
import org.apache.commons.vfs2.provider.local.DefaultLocalFileProvider

internal class LocalTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { LocalTransferProtocolProvider() }
        private val localFileProvider by lazy { DefaultLocalFileProvider() }

        const val PROTOCOL = "file"
    }

    override fun getFileProvider(): FileProvider {
        return localFileProvider
    }

    override fun getRootFileObject(requester: FileObjectRequester): FileObjectHandler {
        val path = StringUtils.defaultIfBlank(requester.defaultPath, SystemUtils.USER_HOME)
        val file = VFS.getManager().resolveFile("file:///${path}")
        return FileObjectHandler(file)
    }

    override fun isTransient(): Boolean {
        return true
    }

    override fun getProtocol(): String {
        return "file"
    }
}