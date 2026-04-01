package app.termora.terminal.panel.vw.extensions

import app.termora.I18n
import app.termora.Icons
import app.termora.TerminalTab
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.plugin.internal.local.LocalTerminalTab
import app.termora.plugin.internal.ssh.SSHTerminalTab
import app.termora.terminal.panel.FloatingToolbarActionExtension
import app.termora.terminal.panel.vw.CommandHistoryVisualWindow
import app.termora.terminal.panel.vw.VisualWindow
import app.termora.terminal.panel.vw.VisualWindowManager
import com.formdev.flatlaf.util.SystemInfo

class HistoryCommandVisualWindowActionExtension private constructor() : FloatingToolbarActionExtension {

    companion object {
        val instance = HistoryCommandVisualWindowActionExtension()
    }

    override fun createActionButton(visualWindowManager: VisualWindowManager, tab: TerminalTab): AnAction {
        if (tab is SSHTerminalTab || (tab is LocalTerminalTab && (SystemInfo.isLinux || SystemInfo.isMacOS))) {
            return object : AnAction(Icons.history) {
                init {
                    putValue(SHORT_DESCRIPTION, I18n.getString("termora.visual-window.command-history"))
                }

                override fun actionPerformed(evt: AnActionEvent) {
                    val visualWindowPanel = CommandHistoryVisualWindow(tab, visualWindowManager)
                    visualWindowManager.addVisualWindow(visualWindowPanel)
                }
            }
        }
        throw UnsupportedOperationException()
    }

    override fun getVisualWindowClass(tab: TerminalTab): Class<out VisualWindow> {
        throw UnsupportedOperationException()
    }

    override fun ordered(): Long {
        return 4
    }
}