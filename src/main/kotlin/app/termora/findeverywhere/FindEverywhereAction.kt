package app.termora.findeverywhere

import app.termora.AnAction
import app.termora.AnActionEvent
import app.termora.ApplicationScope
import app.termora.Icons
import org.apache.commons.lang3.StringUtils
import java.awt.Component
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

class FindEverywhereAction : AnAction(StringUtils.EMPTY, Icons.find) {

    override fun actionPerformed(evt: AnActionEvent) {

        if (evt.scope.getBoolean("FindEverywhereShown", false)) {
            return
        }

        val source = evt.source
        if (source !is Component) {
            return
        }

        val owner = evt.window
        val dialog = FindEverywhere(owner)
        for (provider in FindEverywhereProvider.getFindEverywhereProviders(ApplicationScope.forWindowScope(owner))) {
            dialog.registerProvider(provider)
        }
        dialog.setLocationRelativeTo(owner)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                evt.scope.putBoolean("FindEverywhereShown", false)
            }
        })
        dialog.isVisible = true

        evt.scope.putBoolean("FindEverywhereShown", true)
    }
}