package app.termora.keymgr

import app.termora.AnAction
import app.termora.AnActionEvent
import app.termora.I18n
import app.termora.Icons

class KeyManagerAction : AnAction(
    I18n.getString("termora.keymgr.title"),
    Icons.greyKey
) {
    override fun actionPerformed(evt: AnActionEvent) {
        if (this.isEnabled) {
            KeyManagerDialog(evt.window).isVisible = true
        }
    }
}