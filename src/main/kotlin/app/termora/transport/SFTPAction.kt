package app.termora.transport

import app.termora.*

class SFTPAction : AnAction("SFTP", Icons.folder) {
    override fun actionPerformed(evt: AnActionEvent) {

        val terminalTabbedManager = evt.scope.get(TerminalTabbedManager::class)
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