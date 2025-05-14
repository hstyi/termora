package app.termora.sftp.internal.sftp

import app.termora.Disposer
import app.termora.protocol.FileObjectHandler
import org.apache.commons.io.IOUtils
import org.apache.commons.vfs2.FileObject
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession

class SFTPFileObjectHandler(
    file: FileObject,
    val client: SshClient,
    val session: ClientSession
) : FileObjectHandler(file) {
    init {
        session.addCloseFutureListener { Disposer.dispose(this) }
    }

    override fun dispose() {
        IOUtils.closeQuietly(session)
        IOUtils.closeQuietly(client)
    }
}