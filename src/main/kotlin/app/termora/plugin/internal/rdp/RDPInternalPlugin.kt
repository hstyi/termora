package app.termora.plugin.internal.rdp

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class RDPInternalPlugin : InternalPlugin() {
    override fun getName(): String {
        return "RDP Protocol"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun getExtensions(): List<Extension> {
        return listOf(RDPProtocolProviderExtension.instance)
    }


}