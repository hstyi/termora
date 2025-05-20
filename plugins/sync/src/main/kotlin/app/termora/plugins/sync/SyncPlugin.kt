package app.termora.plugins.sync

import app.termora.Icons
import app.termora.SettingsOptionExtension
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import org.apache.commons.lang3.StringUtils
import javax.swing.Icon

class SyncPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(SettingsOptionExtension::class.java) { SyncSettingsOptionExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): Icon {
        return Icons.settingSync
    }

    override fun getName(): String {
        return "Sync"
    }

    override fun getDescription(): String {
        return StringUtils.EMPTY
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}