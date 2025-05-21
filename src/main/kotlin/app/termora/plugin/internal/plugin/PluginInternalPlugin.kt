package app.termora.plugin.internal.plugin

import app.termora.SettingsOptionExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class PluginInternalPlugin : InternalPlugin() {
    init {
        support.addExtension(SettingsOptionExtension::class.java) { PluginSettingsOptionExtension.instance }
    }

    override fun getName(): String {
        return "Plugin"
    }

    override fun getDescription(): String {
        return getName()
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}