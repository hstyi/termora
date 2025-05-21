package app.termora.plugin

import app.termora.DynamicIcon
import app.termora.Icons

internal abstract class InternalPlugin() : Plugin {
    protected val support = ExtensionSupport()

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): DynamicIcon {
        return Icons.plugin
    }

}