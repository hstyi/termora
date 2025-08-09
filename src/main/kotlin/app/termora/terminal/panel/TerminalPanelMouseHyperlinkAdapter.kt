package app.termora.terminal.panel

import app.termora.terminal.ClickableHighlighter
import app.termora.terminal.Terminal
import com.formdev.flatlaf.util.SystemInfo
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * 超链接点击时
 */
class TerminalPanelMouseHyperlinkAdapter(
    private val terminalPanel: TerminalPanel,
    private val terminalDisplay: TerminalDisplay,
    private val terminal: Terminal,
) : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {
        if (SwingUtilities.isLeftMouseButton(e).not()) {
            return
        }

        if (SystemInfo.isMacOS) {
            if (e.isMetaDown.not())
                return
        } else if (e.isControlDown.not()) {
            return
        }

        val position = terminalPanel.pointToPosition(e.point)
        for (highlighter in terminal.getMarkupModel().getHighlighters(position)) {
            if (highlighter is ClickableHighlighter) {
                highlighter.onClicked(position)
            }
        }
    }

    override fun mouseMoved(e: MouseEvent) {
        val position = terminalPanel.pointToPosition(e.point)
        var cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
        for (highlighter in terminal.getMarkupModel().getHighlighters(position)) {
            if (highlighter is ClickableHighlighter) {
                cursor = if (SystemInfo.isMacOS) Cursor.getDefaultCursor()
                else Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                break
            }
        }
        terminalDisplay.cursor = cursor
    }


}