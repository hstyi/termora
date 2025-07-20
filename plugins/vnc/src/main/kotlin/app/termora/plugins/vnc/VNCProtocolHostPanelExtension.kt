package app.termora.plugins.vnc

import app.termora.account.AccountOwner
import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

internal class VNCProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance = VNCProtocolHostPanelExtension()

    }

    override fun getProtocolProvider(): ProtocolProvider {
        return VNCProtocolProvider.instance
    }

    override fun createProtocolHostPanel(accountOwner: AccountOwner): ProtocolHostPanel {
        return VNCProtocolHostPanel()
    }

}