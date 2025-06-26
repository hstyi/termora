package app.termora.plugins.cos

import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.PaidPlugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

class COSPlugin : PaidPlugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { COSProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { COSProtocolHostPanelExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Tencent COS"
    }



    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}