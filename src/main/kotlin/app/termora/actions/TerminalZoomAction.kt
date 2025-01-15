package app.termora.actions

import app.termora.ApplicationScope
import app.termora.Database
import app.termora.TerminalPanelFactory

abstract class TerminalZoomAction : AnAction() {
    protected val fontSize get() = Database.getDatabase().terminal.fontSize

    abstract fun zoom(): Boolean

    override fun actionPerformed(evt: AnActionEvent) {
        evt.getData(DataProviders.TerminalPanel) ?: return

        if (zoom()) {
            ApplicationScope.windowScopes().forEach {
                TerminalPanelFactory.getInstance(it)
                    .fireResize()
            }
            evt.consume()
        }
    }
}