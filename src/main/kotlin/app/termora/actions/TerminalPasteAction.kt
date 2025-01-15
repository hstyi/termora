package app.termora.actions

import app.termora.I18n
import org.slf4j.LoggerFactory
import java.awt.datatransfer.DataFlavor

class TerminalPasteAction : AnAction() {
    companion object {
        const val PASTE = "TerminalPaste"
        private val log = LoggerFactory.getLogger(TerminalPasteAction::class.java)
    }


    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.paste-to-terminal"))
        putValue(ACTION_COMMAND_KEY, PASTE)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val terminalPanel = evt.getData(DataProviders.TerminalPanel) ?: return
        val systemClipboard = terminalPanel.toolkit.systemClipboard
        if (systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            val text = systemClipboard.getData(DataFlavor.stringFlavor)
            if (text is String) {
                terminalPanel.paste(text)
                if (log.isTraceEnabled) {
                    log.info("Paste {}", text)
                }
            }
        }
        evt.consume()
    }


}