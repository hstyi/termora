package app.termora.plugins.ftp

import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension
import org.apache.commons.lang3.StringUtils
import javax.swing.Icon

class FTPPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { FTPProtocolProviderExtension.Companion.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { FTPProtocolHostPanelExtension.Companion.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): Icon {
        return Icons.ftp
    }

    override fun getName(): String {
        return "FTP"
    }

    override fun getDescription(): String {
        return StringUtils.EMPTY
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}