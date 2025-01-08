package app.termora.terminal.panel

import com.formdev.flatlaf.util.SystemInfo
import org.slf4j.LoggerFactory
import java.awt.datatransfer.DataFlavor
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalPasteAction(private val terminalPanel: TerminalPanel) : TerminalPredicateAction {
    companion object {
        private val log = LoggerFactory.getLogger(TerminalPasteAction::class.java)
    }

    private val systemClipboard get() = terminalPanel.toolkit.systemClipboard

    override fun actionPerformed(e: KeyEvent) {
        if (systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            val text = systemClipboard.getData(DataFlavor.stringFlavor)
            if (text is String) {
                terminalPanel.paste(text)
                if (log.isTraceEnabled) {
                    log.info("Paste {}", text)
                }
            }
        }
    }

    override fun test(keyStroke: KeyStroke, e: KeyEvent): Boolean {
        if (SystemInfo.isMacOS) {
            return KeyStroke.getKeyStroke(KeyEvent.VK_V, terminalPanel.toolkit.menuShortcutKeyMaskEx) == keyStroke
        }

        // Shift + Insert
        val keyStroke1 = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK)
        // Ctrl + Shift + V
        val keyStroke2 = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)

        return keyStroke == keyStroke1 || keyStroke == keyStroke2
    }

}