package app.termora.terminal.panel.vw.extensions

import app.termora.I18n
import app.termora.Icons
import app.termora.PtyHostTerminalTab
import app.termora.TerminalTab
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.snippet.SnippetAction
import app.termora.snippet.SnippetTreeDialog
import app.termora.terminal.panel.FloatingToolbarActionExtension
import app.termora.terminal.panel.vw.VisualWindow
import app.termora.terminal.panel.vw.VisualWindowManager
import javax.swing.JComponent

class SnippetVisualWindowActionExtension private constructor() : FloatingToolbarActionExtension {

    companion object {
        val instance = SnippetVisualWindowActionExtension()
    }

    override fun createActionButton(visualWindowManager: VisualWindowManager, tab: TerminalTab): AnAction {
        if (tab !is PtyHostTerminalTab) throw UnsupportedOperationException()
        return object : AnAction(Icons.codeSpan) {
            init {
                putValue(SHORT_DESCRIPTION, I18n.getString("termora.snippet.title"))
            }

            override fun actionPerformed(evt: AnActionEvent) {
                val btn = evt.source as? JComponent ?: return
                val writer = tab.getData(DataProviders.TerminalWriter) ?: return
                val dialog = SnippetTreeDialog(evt.window)
                dialog.setLocationRelativeTo(btn)
                dialog.setLocation(dialog.x, btn.locationOnScreen.y + btn.height + 2)
                dialog.isVisible = true
                val node = dialog.getSelectedNode() ?: return
                SnippetAction.getInstance().runSnippet(node.data, writer)
            }
        }
    }

    override fun getVisualWindowClass(tab: TerminalTab): Class<out VisualWindow> {
        throw UnsupportedOperationException()
    }

    override fun ordered(): Long {
        return 2;
    }
}