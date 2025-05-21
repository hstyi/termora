package app.termora.account

import app.termora.SettingsOptionExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class AccountPlugin : InternalPlugin() {
    init {
        support.addExtension(SettingsOptionExtension::class.java) { AccountSettingsOptionExtension.instance }
    }

    override fun getName(): String {
        return "Account"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }
}