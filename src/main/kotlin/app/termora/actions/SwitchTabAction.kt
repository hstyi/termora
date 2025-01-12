package app.termora.actions

import java.awt.event.KeyEvent

class SwitchTabAction : AnAction() {
    companion object {
        const val SWITCH_TAB = "SwitchTabAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, SWITCH_TAB)
        putValue(SHORT_DESCRIPTION, "Switch to specific Tab [1..9]")
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val original = evt.event
        if (original !is KeyEvent) return
        if (original.keyCode !in KeyEvent.VK_1..KeyEvent.VK_9) return

        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val tabs = terminalTabbedManager.getTerminalTabs()
        if (tabs.isEmpty()) return


        val tabIndex = original.keyCode - KeyEvent.VK_1
        if (tabIndex >= tabs.size) {
            terminalTabbedManager.setSelectedTerminalTab(tabs.last())
        } else {
            terminalTabbedManager.setSelectedTerminalTab(tabs[tabIndex])
        }

        evt.consume()

    }
}