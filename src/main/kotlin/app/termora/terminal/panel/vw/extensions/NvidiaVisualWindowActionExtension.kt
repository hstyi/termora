package app.termora.terminal.panel.vw.extensions

import app.termora.I18n
import app.termora.Icons
import app.termora.TerminalTab
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.plugin.internal.ssh.SSHTerminalTab
import app.termora.terminal.panel.FloatingToolbarActionExtension
import app.termora.terminal.panel.vw.NvidiaSMIVisualWindow
import app.termora.terminal.panel.vw.VisualWindow
import app.termora.terminal.panel.vw.VisualWindowManager

class NvidiaVisualWindowActionExtension private constructor() : FloatingToolbarActionExtension {

    companion object {
        val instance = NvidiaVisualWindowActionExtension()
    }

    override fun createActionButton(visualWindowManager: VisualWindowManager, tab: TerminalTab): AnAction {
        if (tab !is SSHTerminalTab) throw UnsupportedOperationException()
        return object : AnAction(Icons.nvidia) {
            init {
                putValue(SHORT_DESCRIPTION, I18n.getString("termora.visual-window.nvidia-smi"))
            }

            override fun actionPerformed(evt: AnActionEvent) {
                val visualWindowPanel = NvidiaSMIVisualWindow(tab, visualWindowManager)
                visualWindowManager.addVisualWindow(visualWindowPanel)
            }
        }
    }

    override fun getVisualWindowClass(tab: TerminalTab): Class<out VisualWindow> {
        return NvidiaSMIVisualWindow::class.java
    }

    override fun ordered(): Long {
        return 3;
    }
}