package app.termora.actions

import app.termora.*

class OpenHostAction : AnAction() {
    override fun actionPerformed(evt: AnActionEvent) {
        val event = evt.event
        if (event !is OpenHostActionEvent) return

        val tab = if (event.host.protocol == Protocol.SSH)
            SSHTerminalTab(ApplicationScope.forWindowScope(evt.window), event.host)
        else LocalTerminalTab(ApplicationScope.forWindowScope(evt.window), event.host)

        evt.scope.get(TerminalTabbedManager::class).addTerminalTab(tab)
        tab.start()
    }
}