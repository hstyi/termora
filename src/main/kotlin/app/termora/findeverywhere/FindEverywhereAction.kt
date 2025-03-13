package app.termora.findeverywhere

import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import org.apache.commons.lang3.StringUtils
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

class FindEverywhereAction : AnAction(StringUtils.EMPTY, Icons.find) {
    companion object {

        /**
         * 查找
         */
        const val FIND_EVERYWHERE = "FindEverywhereAction"

    }

    init {
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.open-find-everywhere"))
        putValue(NAME, I18n.getString("termora.find-everywhere"))
        putValue(ACTION_COMMAND_KEY, FIND_EVERYWHERE)
    }

    override fun actionPerformed(evt: AnActionEvent) {

        val scope = evt.getData(DataProviders.WindowScope) ?: return
        if (scope.getBoolean("FindEverywhereShown", false)) {
            return
        }

        val source = evt.source
        if (source !is Component) {
            return
        }

        val owner = evt.window
        val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow

        if (owner != focusedWindow) {
            return
        }

        val dialog = FindEverywhere(owner, scope)
        for (provider in FindEverywhereProvider.getFindEverywhereProviders(scope)) {
            dialog.registerProvider(provider)
        }
        dialog.setLocationRelativeTo(owner)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                scope.putBoolean("FindEverywhereShown", false)
            }
        })
        dialog.isVisible = true

        scope.putBoolean("FindEverywhereShown", true)
    }
}