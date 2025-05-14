package app.termora.plugin

import app.termora.Icons
import javax.swing.Icon

internal abstract class InternalPlugin() : Plugin {
    protected val support = ExtensionSupport()

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): Icon {
        return Icons.plugin
    }

}