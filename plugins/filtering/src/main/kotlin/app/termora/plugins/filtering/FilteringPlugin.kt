package app.termora.plugins.filtering

import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.terminal.panel.FloatingToolbarActionExtension

class FilteringPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(FloatingToolbarActionExtension::class.java) { FilteringFloatingToolbarActionExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }


    override fun getName(): String {
        return "Filtering Terminal"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}