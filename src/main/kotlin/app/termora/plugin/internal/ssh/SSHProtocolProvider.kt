package app.termora.plugin.internal.ssh

import app.termora.Host
import app.termora.Protocol
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider
import app.termora.protocol.ProtocolProvider

internal class SSHProtocolProvider private constructor() : ProtocolProvider {
    companion object {
        val instance by lazy { SSHProtocolProvider() }
    }

    override fun getProtocol(): String {
        return Protocol.SSH.name
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return SSHTerminalTab(windowScope, host)
    }

}