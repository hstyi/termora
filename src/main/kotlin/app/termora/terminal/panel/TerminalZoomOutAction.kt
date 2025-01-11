package app.termora.terminal.panel

import app.termora.Database
import java.awt.Toolkit
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import kotlin.math.max

class TerminalZoomOutAction : TerminalZoomAction(
    KeyStroke.getKeyStroke(
        KeyEvent.VK_MINUS,
        Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    )
) {

    override fun zoom(): Boolean {
        val oldFontSize = fontSize
        Database.getDatabase().terminal.fontSize = max(fontSize - 2, 9)
        return oldFontSize != fontSize
    }
}