package app.termora.plugins.bg

import app.termora.GlassPaneExtension
import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import org.apache.commons.lang3.StringUtils
import javax.swing.Icon

class BGPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(GlassPaneExtension::class.java) { UrlGlassPaneExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): Icon {
        return Icons.huawei
    }

    override fun getName(): String {
        return "Url Background"
    }

    override fun getDescription(): String {
        return StringUtils.EMPTY
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}