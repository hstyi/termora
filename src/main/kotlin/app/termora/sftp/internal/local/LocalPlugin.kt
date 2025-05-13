package app.termora.sftp.internal.local

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class LocalPlugin : InternalPlugin() {

    override fun getName(): String {
        return "Local Transfer"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun getExtensions(): List<Extension> {
        return listOf(LocalProtocolProviderExtension.instance)
    }
}