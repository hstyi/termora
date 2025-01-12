package app.termora.actions

import app.termora.Database
import app.termora.TerminalPanelFactory

abstract class TerminalZoomAction : AnAction() {
    protected val fontSize get() = Database.getDatabase().terminal.fontSize

    abstract fun zoom(): Boolean

    override fun actionPerformed(evt: AnActionEvent) {
        val windowScope = evt.getData(DataProviders.WindowScope) ?: return
        evt.getData(DataProviders.TerminalPanel) ?: return

        if (zoom()) {
            TerminalPanelFactory.getInstance(windowScope).fireResize()
            evt.consume()
        }
    }
}