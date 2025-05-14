package app.termora.plugin.internal.local

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import app.termora.protocol.ProtocolTestRequester
import app.termora.protocol.ProtocolTester
import java.awt.Window

internal class LocalProtocolProvider private constructor() : GenericProtocolProvider, ProtocolTester {
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

    override fun canTestConnection(requester: ProtocolTestRequester): Boolean {
        return true
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return LocalTerminalTab(windowScope, host)
    }

}