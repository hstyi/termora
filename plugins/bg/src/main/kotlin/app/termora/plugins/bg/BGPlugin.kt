package app.termora.plugins.bg

import app.termora.*
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin

class BGPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(GlassPaneExtension::class.java) { BGGlassPaneExtension.instance }
        support.addExtension(SettingsOptionExtension::class.java) { BackgroundSettingsOptionExtension.instance }
        support.addExtension(ApplicationRunnerExtension::class.java) { BackgroundApplicationRunnerExtension.instance }
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