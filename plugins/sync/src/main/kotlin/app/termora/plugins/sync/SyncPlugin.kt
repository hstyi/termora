package app.termora.plugins.sync

import app.termora.DynamicIcon
import app.termora.I18n
import app.termora.Icons
import app.termora.SettingsOptionExtension
import app.termora.db.DatabaseManagerExtension
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin

class SyncPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(SettingsOptionExtension::class.java) { SyncSettingsOptionExtension.instance }
        support.addExtension(DatabaseManagerExtension::class.java) { SyncDatabaseManagerExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): DynamicIcon {
        return Icons.cloud
    }

    override fun getName(): String {
        return "Sync"
    }

    override fun getDescription(): String {
        return I18n.getString("termora.plugins.sync.description")
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}