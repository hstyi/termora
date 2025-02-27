package app.termora.snippet

import app.termora.ApplicationScope
import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.terminal.ControlCharacters
import app.termora.terminal.DataKey
import app.termora.terminal.Terminal

class SnippetAction private constructor() : AnAction(I18n.getString("termora.snippet.title"), Icons.codeSpan) {
    companion object {
        fun getInstance(): SnippetAction {
            return ApplicationScope.forApplicationScope().getOrCreate(SnippetAction::class) { SnippetAction() }
        }

        const val SNIPPET = "SnippetAction"
    }

    override fun actionPerformed(evt: AnActionEvent) {
        SnippetDialog(evt.window).isVisible = true
    }


    fun runSnippet(snippet: Snippet, terminal: Terminal) {
        if (snippet.type != SnippetType.Snippet) return
        val terminalModel = terminal.getTerminalModel()
        val map = mapOf(
            "\\r" to ControlCharacters.CR,
            "\\n" to ControlCharacters.LF,
            "\\t" to ControlCharacters.TAB,
            "\\a" to ControlCharacters.BEL,
            "\\e" to ControlCharacters.ESC,
            "\\b" to ControlCharacters.BS,
        )

        if (terminalModel.hasData(DataKey.PtyConnector)) {
            var text = snippet.snippet
            for (e in map.entries) {
                text = text.replace(e.key, e.value.toString())
            }
            val ptyConnector = terminalModel.getData(DataKey.PtyConnector)
            ptyConnector.write(text.toByteArray(ptyConnector.getCharset()))
        }
    }
}