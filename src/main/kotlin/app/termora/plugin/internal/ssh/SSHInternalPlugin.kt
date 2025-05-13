package app.termora.plugin.internal.ssh

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class SSHInternalPlugin : InternalPlugin() {
    override fun getName(): String {
        return "SSH Protocol"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun getExtensions(): List<Extension> {
        return listOf(SSHProtocolProviderExtension.instance)
    }


}