package app.termora.plugin.internal.wsl

import app.termora.account.AccountOwner
import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

internal class WSLProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { WSLProtocolHostPanelExtension() }

    }

    override fun getProtocolProvider(): ProtocolProvider {
        return WSLProtocolProvider.instance
    }

    override fun canCreateProtocolHostPanel(accountOwner: AccountOwner): Boolean {
        return WSLSupport.isSupported
    }

    override fun createProtocolHostPanel(accountOwner: AccountOwner): ProtocolHostPanel {
        return WSLProtocolHostPanel()
    }
}