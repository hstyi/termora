package app.termora.vfs2.sftp

import org.apache.commons.vfs2.FileSystem
import org.apache.commons.vfs2.FileSystemConfigBuilder
import org.apache.commons.vfs2.FileSystemOptions
import org.apache.sshd.client.session.ClientSession

class MySftpFileSystemConfigBuilder : FileSystemConfigBuilder() {

    companion object {
        private val INSTANCE by lazy { MySftpFileSystemConfigBuilder() }
        fun getInstance(): MySftpFileSystemConfigBuilder {
            return INSTANCE
        }
    }

    override fun getConfigClass(): Class<out FileSystem> {
        return MySftpFileSystem::class.java
    }


    fun setClientSession(options: FileSystemOptions, session: ClientSession) {
        setParam(options, "session", session)
    }

    fun getClientSession(options: FileSystemOptions): ClientSession? {
        return getParam(options, "session")
    }
}