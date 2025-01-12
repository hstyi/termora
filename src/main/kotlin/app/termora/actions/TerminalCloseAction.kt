package app.termora.actions

class TerminalCloseAction : AnAction() {
    companion object {
        const val CLOSE = "Close"
    }

    init {
        putValue(SHORT_DESCRIPTION, "Close Tab")
        putValue(ACTION_COMMAND_KEY, CLOSE)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        terminalTabbedManager.getSelectedTerminalTab()?.let {
            terminalTabbedManager.closeTerminalTab(it)
            evt.consume()
        }
    }


}