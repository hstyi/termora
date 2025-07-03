package app.termora.terminal.panel.vw.extensions

import app.termora.Icons
import app.termora.TerminalTab
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.plugin.internal.ssh.SSHTerminalTab
import app.termora.terminal.panel.FloatingToolbarActionExtension
import app.termora.terminal.panel.vw.TransferVisualWindow
import app.termora.terminal.panel.vw.VisualWindow
import app.termora.terminal.panel.vw.VisualWindowManager

class TransferVisualWindowActionExtension private constructor() : FloatingToolbarActionExtension {

    companion object {
        val instance = TransferVisualWindowActionExtension()
    }

    override fun createActionButton(visualWindowManager: VisualWindowManager, tab: TerminalTab): AnAction {
        if (tab !is SSHTerminalTab) throw UnsupportedOperationException()
        return object : AnAction(Icons.folder) {
            override fun actionPerformed(evt: AnActionEvent) {
                val visualWindowPanel = TransferVisualWindow(tab, visualWindowManager)
                visualWindowManager.addVisualWindow(visualWindowPanel)
            }
        }
    }

    override fun getVisualWindowClass(tab: TerminalTab): Class<out VisualWindow> {
        if (tab !is SSHTerminalTab) throw UnsupportedOperationException()
        return TransferVisualWindow::class.java
    }

    override fun ordered(): Long {
        return 1
    }
}