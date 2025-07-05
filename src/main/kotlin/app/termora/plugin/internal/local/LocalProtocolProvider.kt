package app.termora.plugin.internal.local

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider

internal class LocalProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { LocalProtocolProvider() }
        const val PROTOCOL = "local"
    }

    override fun getProtocol(): String {
        return "Local"
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.powershell
    }


    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return LocalTerminalTab(windowScope, host)
    }

}