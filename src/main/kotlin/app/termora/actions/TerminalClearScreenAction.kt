package app.termora.actions

import app.termora.I18n

class TerminalClearScreenAction : AnAction() {
    companion object {
        const val CLEAR_SCREEN = "ClearScreen"
    }

    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.clear-screen"))
        putValue(ACTION_COMMAND_KEY, CLEAR_SCREEN)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminal = evt.getData(DataProviders.Terminal) ?: return
        terminal.getDocument().eraseInDisplay(3)
        evt.consume()
    }


}