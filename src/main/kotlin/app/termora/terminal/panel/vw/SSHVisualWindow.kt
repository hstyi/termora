package app.termora.terminal.panel.vw

import app.termora.HostTerminalTab
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import org.apache.commons.lang3.StringUtils
import java.util.*

abstract class SSHVisualWindow(
    protected val tab: HostTerminalTab,
    id: String,
    visualWindowManager: VisualWindowManager
) : VisualWindowPanel(id, visualWindowManager), Resumeable {

    override fun toggleWindow() {
        val evt = AnActionEvent(tab.getJComponent(), StringUtils.EMPTY, EventObject(this))
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return

        super.toggleWindow()

        if (!isWindow()) {
            terminalTabbedManager.setSelectedTerminalTab(tab)
        }
    }


    override fun getWindowTitle(): String {
        return tab.getTitle() + " - " + title
    }
}