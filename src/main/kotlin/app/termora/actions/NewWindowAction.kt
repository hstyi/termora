package app.termora.actions

import app.termora.I18n
import app.termora.TermoraFrameManager
import java.awt.KeyboardFocusManager

class NewWindowAction : AnAction() {
    companion object {

        /**
         * 打开一个新的窗口
         */
        const val NEW_WINDOW = "NewWindowAction"
    }

    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.open-new-window"))
        putValue(ACTION_COMMAND_KEY, NEW_WINDOW)
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        if (focusedWindow == evt.window) {
            TermoraFrameManager.getInstance().createWindow().isVisible = true
        }
    }
}