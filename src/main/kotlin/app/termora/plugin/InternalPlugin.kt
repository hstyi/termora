package app.termora.plugin

import app.termora.Application
import app.termora.Icons
import javax.swing.Icon

internal abstract class InternalPlugin : Plugin {
    override fun getVersion(): String {
        return Application.getVersion()
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): Icon {
        return Icons.plugin
    }

}