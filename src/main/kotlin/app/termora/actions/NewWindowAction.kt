package app.termora.actions

import app.termora.AnAction
import app.termora.AnActionEvent
import app.termora.TermoraFrameManager

class NewWindowAction : AnAction() {

    override fun actionPerformed(evt: AnActionEvent) {
        TermoraFrameManager.getInstance().createWindow().isVisible = true
    }
}