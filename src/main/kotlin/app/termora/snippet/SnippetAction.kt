package app.termora.snippet

import app.termora.ApplicationScope
import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.terminal.ControlCharacters
import app.termora.terminal.panel.TerminalWriter

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


    fun runSnippet(snippet: Snippet, writer: TerminalWriter) {
        if (snippet.type != SnippetType.Snippet) return
        val map = mapOf(
            "\\r" to ControlCharacters.CR,
            "\\n" to ControlCharacters.LF,
            "\\t" to ControlCharacters.TAB,
            "\\a" to ControlCharacters.BEL,
            "\\e" to ControlCharacters.ESC,
            "\\b" to ControlCharacters.BS,
        )

        var text = snippet.snippet
        for (e in map.entries) {
            text = text.replace(e.key, e.value.toString())
        }
        writer.write(TerminalWriter.WriteRequest.fromBytes(text.toByteArray(writer.getCharset())))
    }
}