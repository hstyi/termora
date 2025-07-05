package app.termora.plugin.internal.telnet

import app.termora.account.AccountOwner
import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

internal class TelnetProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance = TelnetProtocolHostPanelExtension()

    }

    override fun getProtocolProvider(): ProtocolProvider {
        return TelnetProtocolProvider.instance
    }

    override fun createProtocolHostPanel(accountOwner: AccountOwner): ProtocolHostPanel {
        return TelnetProtocolHostPanel(accountOwner)
    }

    override fun ordered(): Long {
        return 4
    }
}