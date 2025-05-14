package app.termora.plugins.cos

import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension
import org.apache.commons.lang3.StringUtils
import javax.swing.Icon

class COSPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { COSProtocolProviderExtension.Companion.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { COSProtocolHostPanelExtension.Companion.instance }
    }

    override fun getVersion(): String {
        return "1.0.0"
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): Icon {
        return Icons.huawei
    }

    override fun getName(): String {
        return "COS"
    }

    override fun getDescription(): String {
        return StringUtils.EMPTY
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}