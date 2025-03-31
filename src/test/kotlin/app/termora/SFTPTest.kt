package app.termora

import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class SFTPTest : SSHDTest() {


    @Test
    fun test() {

        val client = SshClients.openClient(host)
        val session = SshClients.openSession(host, client)
        assertTrue(session.isOpen)


        val fileSystem = DefaultSftpClientFactory.INSTANCE.createSftpFileSystem(session)
        for (path in Files.list(fileSystem.rootDirectories.first())) {
            println(path)
        }
    }
}