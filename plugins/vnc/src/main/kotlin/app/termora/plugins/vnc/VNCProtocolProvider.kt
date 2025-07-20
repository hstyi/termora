package app.termora.plugins.vnc

import app.termora.DynamicIcon
import app.termora.Host
import app.termora.TerminalTab
import app.termora.WindowScope
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider

internal class VNCProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance = VNCProtocolProvider()
        const val PROTOCOL = "VNC"
        private val icon =
            DynamicIcon(
                "META-INF/pluginIcon.svg",
                "META-INF/pluginIcon_dark.svg",
                loader = VNCPlugin::class.java.classLoader
            )
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        return VNCTerminalTab(windowScope, host)
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return icon
    }

}