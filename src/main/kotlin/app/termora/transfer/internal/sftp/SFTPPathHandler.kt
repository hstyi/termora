package app.termora.transfer.internal.sftp

import app.termora.Disposer
import app.termora.protocol.PathHandler
import org.apache.commons.io.IOUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.future.CloseFuture
import org.apache.sshd.common.future.SshFutureListener
import java.nio.file.FileSystem
import java.nio.file.Path

internal class SFTPPathHandler(
    fileSystem: FileSystem,
    path: Path,
    val client: SshClient,
    val session: ClientSession,
) : PathHandler(fileSystem, path) {

    private val listener = SshFutureListener<CloseFuture> { Disposer.dispose(this) }

    init {
        session.addCloseFutureListener(listener)
    }

    override fun dispose() {
        super.dispose()
        session.removeCloseFutureListener(listener)
        IOUtils.closeQuietly(session)
        IOUtils.closeQuietly(client)
    }
}