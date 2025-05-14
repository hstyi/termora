package app.termora.plugin.internal.ssh

import app.termora.DynamicIcon
import app.termora.Host
import app.termora.Icons
import app.termora.SshClients
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import java.awt.Window

internal class SSHProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { SSHProtocolProvider() }
        const val PROTOCOL = "SSH"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return SSHTerminalTab(windowScope, host)
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.ssh
    }

    override fun canTestConnection(owner: Window?, host: Host) = true

    override fun testConnection(owner: Window?, host: Host) {
        var client: SshClient? = null
        var session: ClientSession? = null
        try {
            client = if (owner != null) SshClients.openClient(host, owner) else SshClients.openClient(host)
            session = SshClients.openSession(host, client)
        } finally {
            session?.close()
            client?.close()
        }
    }

    override fun ordered() = Int.MIN_VALUE
}