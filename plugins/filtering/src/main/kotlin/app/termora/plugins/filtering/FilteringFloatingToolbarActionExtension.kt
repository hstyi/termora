package app.termora.plugins.filtering

import app.termora.DynamicIcon
import app.termora.PtyHostTerminalTab
import app.termora.TerminalTab
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.terminal.panel.FloatingToolbarActionExtension
import app.termora.terminal.panel.vw.VisualWindow
import app.termora.terminal.panel.vw.VisualWindowManager

class FilteringFloatingToolbarActionExtension private constructor() : FloatingToolbarActionExtension {
    companion object {
        val instance = FilteringFloatingToolbarActionExtension()
    }

    private val icon = DynamicIcon(
        "META-INF/pluginIcon.svg",
        "META-INF/pluginIcon_dark.svg",
        loader = FilteringPlugin::class.java.classLoader
    )

    override fun createActionButton(
        visualWindowManager: VisualWindowManager,
        tab: TerminalTab
    ): AnAction {
        if (tab !is PtyHostTerminalTab) throw UnsupportedOperationException()

        return object : AnAction(icon) {
            init {
                putValue(SHORT_DESCRIPTION, "Filtering Terminal")
            }

            override fun actionPerformed(evt: AnActionEvent) {
                val visualWindowPanel = FilteringVisualWindow(tab, visualWindowManager)
                visualWindowManager.addVisualWindow(visualWindowPanel)
            }
        }
    }

    override fun getVisualWindowClass(tab: TerminalTab): Class<out VisualWindow> {
        return FilteringVisualWindow::class.java
    }
}