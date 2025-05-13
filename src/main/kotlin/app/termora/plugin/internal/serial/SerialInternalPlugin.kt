package app.termora.plugin.internal.serial

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class SerialInternalPlugin : InternalPlugin() {
    override fun getName(): String {
        return "Serial Protocol"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun getExtensions(): List<Extension> {
        return listOf(SerialProtocolProviderExtension.instance)
    }


}