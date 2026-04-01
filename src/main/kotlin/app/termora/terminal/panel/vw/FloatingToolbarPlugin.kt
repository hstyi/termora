package app.termora.terminal.panel.vw

import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin
import app.termora.terminal.panel.FloatingToolbarActionExtension
import app.termora.terminal.panel.vw.extensions.*

internal class FloatingToolbarPlugin : InternalPlugin() {
    init {
        support.addExtension(FloatingToolbarActionExtension::class.java) { TransferVisualWindowActionExtension.instance }
        support.addExtension(FloatingToolbarActionExtension::class.java) { ServerInfoVisualWindowActionExtension.instance }
        support.addExtension(FloatingToolbarActionExtension::class.java) { SnippetVisualWindowActionExtension.instance }
        support.addExtension(FloatingToolbarActionExtension::class.java) { NvidiaVisualWindowActionExtension.instance }
        support.addExtension(FloatingToolbarActionExtension::class.java) { HistoryCommandVisualWindowActionExtension.instance }
    }

    override fun getName(): String {
        return "FloatingToolbar"
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }

}