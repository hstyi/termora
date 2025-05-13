package app.termora.plugin.internal.sftppty

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class SFTPPtyInternalPlugin : InternalPlugin() {
    override fun getName(): String {
        return "SFTP Pty Protocol"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun getExtensions(): List<Extension> {
        return listOf(SFTPPtyProtocolProviderExtension.instance)
    }


}