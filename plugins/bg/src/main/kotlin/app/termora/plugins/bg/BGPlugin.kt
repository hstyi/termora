package app.termora.plugins.bg

import app.termora.DynamicIcon
import app.termora.GlassPaneExtension
import app.termora.I18n
import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.PaidPlugin

class BGPlugin : PaidPlugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(GlassPaneExtension::class.java) { UrlGlassPaneExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): DynamicIcon {
        return Icons.image
    }

    override fun getName(): String {
        return "Customize Background"
    }

    override fun getDescription(): String {
        return I18n.getString("termora.plugins.bg.description")
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}