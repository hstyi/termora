package app.termora.plugins.editor

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin

class EditorPlugin : Plugin {
    private val support = ExtensionSupport()

    init {
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): DynamicIcon {
        return Icons.edit
    }

    override fun getName(): String {
        return "File Editor"
    }

    override fun getDescription(): String {
        return getName()
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}