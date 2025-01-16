package app.termora.terminal.panel

import app.termora.terminal.PtyConnector
import app.termora.terminal.Terminal
import com.formdev.flatlaf.util.SystemInfo
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TerminalPanelKeyAdapter(
    private val terminalPanel: TerminalPanel,
    private val terminal: Terminal,
    private val ptyConnector: PtyConnector
) :
    KeyAdapter() {

    override fun keyTyped(e: KeyEvent) {
        if (Character.isISOControl(e.keyChar)) {
            return
        }

        terminal.getSelectionModel().clearSelection()
        ptyConnector.write("${e.keyChar}")
        terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)

    }

    override fun keyPressed(e: KeyEvent) {
        if (e.isConsumed) return

        // remove all toast
        if (e.keyCode == KeyEvent.VK_ESCAPE) {
            terminalPanel.hideToast()
        }

        val keyStroke = KeyStroke.getKeyStrokeForEvent(e)
        for (action in terminalPanel.getTerminalActions()) {
            if (action.test(keyStroke, e)) {
                action.actionPerformed(e)
                return
            }
        }

        val encode = terminal.getKeyEncoder().encode(AWTTerminalKeyEvent(e))
        if (encode.isNotEmpty()) {
            ptyConnector.write(encode)
        }

        // https://github.com/TermoraDev/termora/issues/52
        if (SystemInfo.isWindows && e.keyCode == KeyEvent.VK_TAB && isCtrlPressedOnly(e)) {
            return
        }

        if (Character.isISOControl(e.keyChar) && isCtrlPressedOnly(e)) {
            terminal.getSelectionModel().clearSelection()
            // 如果不为空表示已经发送过了，所以这里为空的时候再发送
            if (encode.isEmpty()) {
                ptyConnector.write("${e.keyChar}")
            }
            terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)
        }

    }

    private fun isCtrlPressedOnly(e: KeyEvent): Boolean {
        val modifiersEx = e.modifiersEx
        return (modifiersEx and InputEvent.ALT_DOWN_MASK) == 0
                && (modifiersEx and InputEvent.ALT_GRAPH_DOWN_MASK) == 0
                && (modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
                && (modifiersEx and InputEvent.SHIFT_DOWN_MASK) == 0
    }
}