package app.termora.plugin.internal.local

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class LocalInternalPlugin : InternalPlugin() {
    override fun getName(): String {
        return "Local Protocol"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun getExtensions(): List<Extension> {
        return listOf(LocalProtocolProviderExtension.instance)
    }


}