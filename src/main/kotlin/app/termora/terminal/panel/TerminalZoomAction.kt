package app.termora.terminal.panel

import app.termora.ApplicationScope
import app.termora.TerminalPanelFactory
import app.termora.Database
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

abstract class TerminalZoomAction(keyStroke: KeyStroke) : TerminalAction(keyStroke) {
    protected val fontSize get() = Database.getDatabase().terminal.fontSize

    override fun actionPerformed(e: KeyEvent) {
        if (!zoom()) return
        TerminalPanelFactory.getInstance(ApplicationScope.forWindowScope(e.component)).fireResize()
    }

    abstract fun zoom(): Boolean
}