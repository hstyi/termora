package app.termora.actions

class TerminalClearScreenAction : AnAction() {
    companion object {
        const val CLEAR_SCREEN = "ClearScreen"
    }

    init {
        putValue(SHORT_DESCRIPTION, "Clear Terminal Buffer")
        putValue(ACTION_COMMAND_KEY, CLEAR_SCREEN)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminal = evt.getData(DataProviders.Terminal) ?: return
        terminal.getDocument().eraseInDisplay(3)
        evt.consume()
    }


}