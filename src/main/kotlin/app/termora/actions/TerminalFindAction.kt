package app.termora.actions

import app.termora.I18n

class TerminalFindAction : AnAction() {
    companion object {
        const val FIND = "TerminalFind"
    }


    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.open-terminal-find"))
        putValue(ACTION_COMMAND_KEY, FIND)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminalPanel = evt.getData(DataProviders.TerminalPanel) ?: return
        terminalPanel.showFind()
        evt.consume()
    }


}