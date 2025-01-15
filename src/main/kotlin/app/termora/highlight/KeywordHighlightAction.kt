package app.termora.highlight

import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent

class KeywordHighlightAction : AnAction(
    I18n.getString("termora.highlight"),
    Icons.edit
) {
    override fun actionPerformed(evt: AnActionEvent) {
        val owner = evt.window
        val dialog = KeywordHighlightDialog(owner)
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
    }
}