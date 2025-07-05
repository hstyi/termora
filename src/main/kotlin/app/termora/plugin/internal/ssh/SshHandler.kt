package app.termora.plugin.internal.ssh

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.channel.Channel

data class SshHandler(
    var client: SshClient? = null,
    var session: ClientSession? = null,
    var channel: Channel? = null
) : AutoCloseable {
    override fun close() {

        channel?.close(true)?.await()
        session?.close(true)?.await()

        channel = null
        session = null


        // client 由 SshSessionPool 负责关闭
        if (client?.isClosing == true || client?.isClosed == true) {
            client = null
        }

    }
}