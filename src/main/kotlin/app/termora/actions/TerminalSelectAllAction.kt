package app.termora.actions

import app.termora.I18n
import app.termora.terminal.Position

class TerminalSelectAllAction : AnAction() {
    companion object {
        const val SELECT_ALL = "TerminalSelectAll"
    }


    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.select-all-in-terminal"))
        putValue(ACTION_COMMAND_KEY, SELECT_ALL)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminal = evt.getData(DataProviders.Terminal) ?: return
        terminal.getSelectionModel().setSelection(
            Position(y = 1, x = 1),
            Position(y = terminal.getDocument().getLineCount(), x = terminal.getTerminalModel().getCols())
        )
        evt.consume()
    }
}