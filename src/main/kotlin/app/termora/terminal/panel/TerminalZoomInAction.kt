package app.termora.terminal.panel

import app.termora.Database
import org.apache.commons.lang3.SystemUtils
import java.awt.Toolkit
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalZoomInAction : TerminalZoomAction(
    KeyStroke.getKeyStroke(
        if (SystemUtils.IS_OS_MAC_OSX) KeyEvent.VK_EQUALS else KeyEvent.VK_PLUS,
        Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    )
) {

    override fun zoom(): Boolean {
        Database.getDatabase().terminal.fontSize += 2
        return true
    }
}