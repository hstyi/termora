package app.termora.macro

import app.termora.*

import org.jdesktop.swingx.action.ActionManager
import javax.swing.Icon

class MacroStartRecordingAction(icon: Icon = Icons.rec) : AnAction(
    I18n.getString("termora.macro.start-recording"),
    icon
) {
    private val macroAction get() = ActionManager.getInstance().getAction(Actions.MACRO) as MacroAction?

    override fun actionPerformed(evt: AnActionEvent) {
        macroAction?.startRecording()
    }

    override fun isEnabled(): Boolean {
        val action = macroAction ?: return false
        return !action.isRecording
    }
}