package app.termora.plugin.internal.wsl

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

internal class WSLInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(ProtocolProviderExtension::class.java) { WSLProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { WSLProtocolHostPanelExtension.instance }
    }

    override fun getName(): String {
        return "WSL Protocol"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}