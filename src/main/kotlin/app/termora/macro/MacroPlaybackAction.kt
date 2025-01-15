package app.termora.macro

import app.termora.Actions
import app.termora.I18n
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import org.jdesktop.swingx.action.ActionManager

class MacroPlaybackAction : AnAction(
    I18n.getString("termora.macro.playback"),
) {
    private val macroAction get() = ActionManager.getInstance().getAction(Actions.MACRO) as MacroAction?
    private val macroManager get() = MacroManager.getInstance()

    override fun actionPerformed(evt: AnActionEvent) {
        val macros = macroManager.getMacros().sortedByDescending { it.sort }
        if (macros.isEmpty() || macroAction == null) {
            return
        }
        macroAction?.runMacro(evt.getData(DataProviders.WindowScope) ?: return, macros.first())
    }

    override fun isEnabled(): Boolean {
        if (macroAction == null) {
            return false
        }
        return macroManager.getMacros().isNotEmpty()
    }
}