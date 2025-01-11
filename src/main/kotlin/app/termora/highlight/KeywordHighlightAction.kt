package app.termora.highlight

import app.termora.*
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window

class KeywordHighlightAction : AnAction(
    I18n.getString("termora.highlight"),
    Icons.edit
) {
    override fun actionPerformed(evt: AnActionEvent) {
        val source = evt.source
        var owner = (if (source is Component) ApplicationScope.getFrameForComponent(source) else null) as Window?
        if (owner == null) {
            owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        }

        if (owner != null) {
            KeywordHighlightDialog(owner).isVisible = true
        }

    }
}