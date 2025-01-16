package app.termora.terminal.panel

import app.termora.actions.AnActionEvent
import app.termora.actions.TerminalPasteAction
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalWindowsPasteAction : TerminalPredicateAction {
    private val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK)

    override fun actionPerformed(e: KeyEvent) {
        ActionManager.getInstance().getAction(TerminalPasteAction.PASTE)
            ?.actionPerformed(AnActionEvent(e.source, StringUtils.EMPTY, e))
    }

    override fun test(keyStroke: KeyStroke, e: KeyEvent): Boolean {
        return (SystemInfo.isWindows || SystemInfo.isLinux) && keyStroke == this.keyStroke
    }
}