package app.termora.actions

import app.termora.LocalTerminalTab
import app.termora.OpenHostActionEvent
import app.termora.Protocol
import app.termora.SSHTerminalTab

class OpenHostAction : AnAction() {
    companion object {
        /**
         * 打开一个主机
         */
        const val OPEN_HOST = "OpenHostAction"
    }

    override fun actionPerformed(evt: AnActionEvent) {
        if (evt !is OpenHostActionEvent) return
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val windowScope = evt.getData(DataProviders.WindowScope) ?: return

        val tab = if (evt.host.protocol == Protocol.SSH)
            SSHTerminalTab(windowScope, evt.host)
        else LocalTerminalTab(windowScope, evt.host)

        terminalTabbedManager.addTerminalTab(tab)
        tab.start()
    }
}