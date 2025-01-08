package app.termora.terminal.panel

import app.termora.I18n
import com.formdev.flatlaf.util.SystemInfo
import org.slf4j.LoggerFactory
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalCopyAction(private val terminalPanel: TerminalPanel) : TerminalPredicateAction {
    companion object {
        private val log = LoggerFactory.getLogger(TerminalCopyAction::class.java)
    }

    private val systemClipboard get() = terminalPanel.toolkit.systemClipboard

    override fun actionPerformed(e: KeyEvent) {
        val text = terminalPanel.copy()

        // 如果文本为空，那么清空剪切板
        if (text.isEmpty()) {
            systemClipboard.setContents(EmptyTransferable(), null)
            return
        }

        systemClipboard.setContents(StringSelection(text), null)
        terminalPanel.toast(I18n.getString("termora.terminal.copied"))
        if (log.isTraceEnabled) {
            log.info("Copy to clipboard. {}", text)
        }
    }

    override fun test(keyStroke: KeyStroke, e: KeyEvent): Boolean {
        if (SystemInfo.isMacOS) {
            return KeyStroke.getKeyStroke(KeyEvent.VK_C, terminalPanel.toolkit.menuShortcutKeyMaskEx) == keyStroke
        }

        // Ctrl + Insert
        val keyStroke1 = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK)
        // Ctrl + Shift + C
        val keyStroke2 = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)

        return keyStroke == keyStroke1 || keyStroke == keyStroke2
    }

    private class EmptyTransferable : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return emptyArray()
        }

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
            return false
        }

        override fun getTransferData(flavor: DataFlavor?): Any {
            throw UnsupportedFlavorException(flavor)
        }

    }
}