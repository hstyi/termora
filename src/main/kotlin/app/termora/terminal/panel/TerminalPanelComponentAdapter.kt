package app.termora.terminal.panel

import app.termora.terminal.Terminal
import org.slf4j.LoggerFactory
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class TerminalPanelComponentAdapter(
    private val terminalPanel: TerminalPanel,
    private val terminalDisplay: TerminalDisplay,
    private val terminal: Terminal,
    private val writer: TerminalWriter
) : ComponentAdapter() {

    companion object {
        private val log = LoggerFactory.getLogger(TerminalPanelComponentAdapter::class.java)
    }


    override fun componentResized(e: ComponentEvent) {
        doResize(terminalDisplay)
    }

    private fun doResize(panel: JComponent) {
        val cols = panel.width / terminalPanel.getAverageCharWidth()
        val rows = panel.height / terminalPanel.getLineHeight()

        // 修改大小
        terminal.getTerminalModel().resize(rows = rows, cols = cols)
        // 修改终端大小
        writer.resize(rows, cols)

        if (log.isTraceEnabled) {
            log.trace("size: {} , cols: {} , rows: {}", terminalPanel.size, cols, rows)
        }
    }

}