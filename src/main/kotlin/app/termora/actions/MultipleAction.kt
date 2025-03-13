package app.termora.actions

import app.termora.I18n
import app.termora.Icons
import app.termora.TerminalPanelFactory

class MultipleAction : AnAction(
    I18n.getString("termora.tools.multiple"),
    Icons.vcs
) {
    init {
        setStateAction()
    }

    override fun actionPerformed(evt: AnActionEvent) {
        TerminalPanelFactory.getInstance().repaintAll()
    }
}