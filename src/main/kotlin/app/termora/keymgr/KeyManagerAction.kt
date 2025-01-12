package app.termora.keymgr

import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent

class KeyManagerAction : AnAction(
    I18n.getString("termora.keymgr.title"),
    Icons.greyKey
) {
    override fun actionPerformed(evt: AnActionEvent) {
        if (this.isEnabled) {
            val dialog = KeyManagerDialog(evt.window)
            dialog.setLocationRelativeTo(evt.window)
            dialog.isVisible = true
        }
    }
}