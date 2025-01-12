package app.termora.macro

import app.termora.Actions
import app.termora.ApplicationScope
import app.termora.I18n
import app.termora.actions.AnAction
import app.termora.findeverywhere.ActionFindEverywhereResult
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereResult
import org.jdesktop.swingx.action.ActionManager
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.Icon
import kotlin.math.min

class MacroFindEverywhereProvider : FindEverywhereProvider {
    private val macroManager get() = MacroManager.getInstance()

    override fun find(pattern: String): List<FindEverywhereResult> {
        val macroAction = ActionManager.getInstance().getAction(Actions.MACRO) ?: return emptyList()
        if (macroAction !is MacroAction) return emptyList()

        val results = mutableListOf<FindEverywhereResult>()

        // recording
        val toggleAction = toggleMacroAction(macroAction)
        if (toggleAction != null) {
            results.add(object : ActionFindEverywhereResult(toggleAction) {
                override fun getIcon(isSelected: Boolean): Icon {
                    if (toggleAction is MacroStopRecordingAction) {
                        return toggleAction.smallIcon
                    }
                    return super.getIcon(isSelected)
                }
            })
        }

        // playback
        val playbackAction = MacroPlaybackAction()
        if (playbackAction.isEnabled) {
            results.add(ActionFindEverywhereResult(playbackAction))
        }

        // macros
        val macros = macroManager.getMacros().sortedByDescending { it.sort }
        for (i in 0 until min(macros.size, 10)) {
            results.add(MacroFindEverywhereResult(macros[i], macroAction))
        }

        return results
    }

    override fun group(): String {
        return I18n.getString("termora.macro")
    }

    private fun toggleMacroAction(macroAction: MacroAction): AnAction? {
        val action = if (macroAction.isRecording) MacroStopRecordingAction() else MacroStartRecordingAction()
        return if (action.isEnabled) action else null
    }

    private class MacroFindEverywhereResult(
        private val macro: Macro,
        private val macroAction: MacroAction
    ) : FindEverywhereResult {
        override fun actionPerformed(e: ActionEvent) {
            val source = e.source
            if (source is Component) {
                macroAction.runMacro(ApplicationScope.forWindowScope(source), macro)
            }
        }

        override fun toString(): String {
            return macro.name
        }
    }

}