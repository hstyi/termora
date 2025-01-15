package app.termora.actions

import app.termora.I18n

class TabReconnectAction : AnAction() {
    companion object {
        const val RECONNECT_TAB = "TabReconnectAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, RECONNECT_TAB)
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.tabbed.contextmenu.reconnect"))
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val tab = evt.getData(DataProviders.TerminalTab) ?: return
        if (tab.canReconnect()) {
            tab.reconnect()
        }
    }
}