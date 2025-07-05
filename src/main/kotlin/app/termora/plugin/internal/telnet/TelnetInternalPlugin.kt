package app.termora.plugin.internal.telnet

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class TelnetInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { TelnetProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { TelnetProtocolHostPanelExtension.instance }
    }

    override fun getName(): String {
        return "Telnet Protocol"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}