package app.termora.terminal.panel

import app.termora.actions.AnActionEvent
import app.termora.actions.TerminalCopyAction
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalWindowsCopyAction : TerminalPredicateAction {
    private val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK)
    override fun actionPerformed(e: KeyEvent) {
        ActionManager.getInstance().getAction(TerminalCopyAction.COPY)
            ?.actionPerformed(AnActionEvent(e.source, StringUtils.EMPTY, e))
    }

    override fun test(keyStroke: KeyStroke, e: KeyEvent): Boolean {
        return (SystemInfo.isWindows || SystemInfo.isLinux) && keyStroke == this.keyStroke
    }
}