package app.termora.plugin.internal.wsl

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider

internal class WSLProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { WSLProtocolProvider() }
        const val PROTOCOL = "WSL"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return WSLHostTerminalTab(windowScope, host)
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.linux
    }

    override fun canCreateTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): Boolean {
        return WSLSupport.isSupported
    }


}