package app.termora.terminal.panel.vw

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.terminal.panel.FloatingToolbarActionExtension
import app.termora.terminal.panel.vw.extensions.NvidiaVisualWindowActionExtension
import app.termora.terminal.panel.vw.extensions.ServerInfoVisualWindowActionExtension
import app.termora.terminal.panel.vw.extensions.SnippetVisualWindowActionExtension
import app.termora.terminal.panel.vw.extensions.TransferVisualWindowActionExtension

internal class FloatingToolbarPlugin : InternalPlugin() {
    init {
        support.addExtension(FloatingToolbarActionExtension::class.java) { TransferVisualWindowActionExtension.instance }
        support.addExtension(FloatingToolbarActionExtension::class.java) { ServerInfoVisualWindowActionExtension.instance }
        support.addExtension(FloatingToolbarActionExtension::class.java) { SnippetVisualWindowActionExtension.instance }
        support.addExtension(FloatingToolbarActionExtension::class.java) { NvidiaVisualWindowActionExtension.instance }
    }

    override fun getName(): String {
        return "FloatingToolbar"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }

}