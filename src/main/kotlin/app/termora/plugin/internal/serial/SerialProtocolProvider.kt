package app.termora.plugin.internal.serial

import app.termora.Host
import app.termora.Serials
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider
import app.termora.protocol.ProtocolProvider
import java.awt.Window

internal class SerialProtocolProvider private constructor() : ProtocolProvider {
    companion object {
        val instance by lazy { SerialProtocolProvider() }
    }

    override fun getProtocol(): String {
        return "Serial"
    }

    override fun testConnection(owner: Window?, host: Host) {
        Serials.openPort(host).closePort()
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return SerialTerminalTab(windowScope, host)
    }

}