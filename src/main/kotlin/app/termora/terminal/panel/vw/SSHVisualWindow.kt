package app.termora.terminal.panel.vw

import app.termora.Disposer
import app.termora.SSHTerminalTab
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import org.apache.commons.lang3.StringUtils
import java.util.*

abstract class SSHVisualWindow(
    protected val tab: SSHTerminalTab,
    id: String,
    visualWindowManager: VisualWindowManager
) : VisualWindowPanel(id, visualWindowManager) {

    init {
        Disposer.register(tab, this)
    }

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