package app.termora.plugins.webdav

import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.PaidPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

class WebDAVPlugin : PaidPlugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { WebDAVProtocolProviderExtension.Companion.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { WebDAVProtocolHostPanelExtension.Companion.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "WebDAV"
    }



    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}