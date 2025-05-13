package app.termora.plugin.internal.local

import app.termora.Host
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import java.awt.Window

internal class LocalProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { LocalProtocolProvider() }
    }

    override fun getProtocol(): String {
        return "Local"
    }

    override fun canTestConnection(owner: Window?, host: Host): Boolean {
        return true
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return LocalTerminalTab(windowScope, host)
    }

}