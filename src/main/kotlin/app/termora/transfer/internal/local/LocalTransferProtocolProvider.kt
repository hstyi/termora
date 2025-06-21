package app.termora.transfer.internal.local

import app.termora.database.DatabaseManager
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import java.nio.file.FileSystems

internal class LocalTransferProtocolProvider : TransferProtocolProvider {
    companion object {
        val instance by lazy { LocalTransferProtocolProvider() }
        private val sftp get() = DatabaseManager.getInstance().sftp
        const val PROTOCOL = "file"
    }

    override fun isTransient(): Boolean {
        return true
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        var defaultDirectory = sftp.defaultDirectory
        if (StringUtils.isBlank(defaultDirectory)) {
            defaultDirectory = SystemUtils.USER_HOME
        }
        val fileSystem = FileSystems.getDefault()
        return PathHandler(fileSystem, fileSystem.getPath(defaultDirectory))
    }
}