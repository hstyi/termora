package app.termora.plugin.internal.serial

import app.termora.DynamicIcon
import app.termora.Host
import app.termora.Icons
import app.termora.Serials
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import java.awt.Window

internal class SerialProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { SerialProtocolProvider() }
        const val PROTOCOL = "Serial"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.serial
    }

    override fun testConnection(owner: Window?, host: Host) {
        Serials.openPort(host).closePort()
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return SerialTerminalTab(windowScope, host)
    }

}