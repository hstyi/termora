package app.termora.actions

import app.termora.*

class MultipleAction : AnAction(
    I18n.getString("termora.tools.multiple"),
    Icons.vcs
) {
    init {
        setStateAction()
    }

    override fun actionPerformed(evt: AnActionEvent) {
        ApplicationScope.windowScopes().map { TerminalPanelFactory.getInstance(it) }
            .forEach { it.repaintAll() }
    }
}