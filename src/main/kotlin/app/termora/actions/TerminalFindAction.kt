package app.termora.actions

class TerminalFindAction : AnAction() {
    companion object {
        const val FIND = "TerminalFind"
    }


    init {
        putValue(SHORT_DESCRIPTION, "Open Terminal Search")
        putValue(ACTION_COMMAND_KEY, FIND)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminalPanel = evt.getData(DataProviders.TerminalPanel) ?: return
        terminalPanel.showFind()
        evt.consume()
    }


}