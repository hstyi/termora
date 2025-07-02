package app.termora.plugins.smb

import app.termora.account.AccountOwner
import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class SMBProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { SMBProtocolHostPanelExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SMBProtocolProvider.instance
    }

    override fun createProtocolHostPanel(accountOwner: AccountOwner): ProtocolHostPanel {
        return SMBProtocolHostPanel()
    }
}