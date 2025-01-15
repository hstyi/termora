package app.termora.transport

import app.termora.Icons
import app.termora.SFTPTerminalTab
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders

class SFTPAction : AnAction("SFTP", Icons.folder) {
    override fun actionPerformed(evt: AnActionEvent) {
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val tabs = terminalTabbedManager.getTerminalTabs()
        for (tab in tabs) {
            if (tab is SFTPTerminalTab) {
                terminalTabbedManager.setSelectedTerminalTab(tab)
                return
            }
        }

        // 创建一个新的
        terminalTabbedManager.addTerminalTab(SFTPTerminalTab())
    }
}