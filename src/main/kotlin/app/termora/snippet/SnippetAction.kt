package app.termora.snippet

import app.termora.ApplicationScope
import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.terminal.ControlCharacters
import app.termora.terminal.Null
import app.termora.terminal.panel.TerminalWriter
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.StringEscapeUtils

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
            "\n" to ControlCharacters.LF,
            "\r" to ControlCharacters.CR,
            "\t" to ControlCharacters.TAB,
            "\b" to ControlCharacters.BS,
            "\\a" to ControlCharacters.BEL,
            "\\e" to ControlCharacters.ESC,
        )
        val chars = snippet.snippet.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            if (i == 0) continue
            if (c != '\n') continue
            if (chars[i - 1] != '\\') continue
            // 每一行的最后一个 \ 比较特殊，先转成 null 然后再去 unescapeJava
            chars[i - 1] = Char.Null
        }

        var text = chars.joinToString(StringUtils.EMPTY)
        text = StringEscapeUtils.unescapeJava(text)
        for (e in map.entries) {
            text = text.replace(e.key, e.value.toString())
        }
        text = snippet.snippet.replace(Char.Null, '\\')

        writer.write(TerminalWriter.WriteRequest.fromBytes(text.toByteArray(writer.getCharset())))
    }
}