package app.termora.sftp.internal.sftp

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class SFTPPlugin : InternalPlugin() {
    override fun getName(): String {
        return "SFTP"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun getExtensions(): List<Extension> {
        return listOf(SFTPProtocolProviderExtension.instance)
    }
}