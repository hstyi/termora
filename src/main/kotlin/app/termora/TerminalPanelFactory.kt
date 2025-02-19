package app.termora

import app.termora.highlight.KeywordHighlightPaintListener
import app.termora.terminal.DataKey
import app.termora.terminal.PtyConnector
import app.termora.terminal.Terminal
import app.termora.terminal.panel.TerminalHyperlinkPaintListener
import app.termora.terminal.panel.TerminalPanel
import kotlinx.coroutines.*
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

class TerminalPanelFactory : Disposable {
    private val terminalPanels = mutableListOf<TerminalPanel>()

    companion object {

        private val Factory = DataKey(TerminalPanelFactory::class)

        fun getInstance(scope: Scope): TerminalPanelFactory {
            return scope.getOrCreate(TerminalPanelFactory::class) { TerminalPanelFactory() }
        }

        fun getAllTerminalPanel(): Array<TerminalPanel> {
            return ApplicationScope.forApplicationScope().windowScopes()
                .map { getInstance(it) }
                .flatMap { it.terminalPanels }.toTypedArray()
        }
    }

    init {
        // repaint
        Painter.getInstance()
    }


    fun createTerminalPanel(terminal: Terminal, ptyConnector: PtyConnector): TerminalPanel {
        val terminalPanel = TerminalPanel(terminal, ptyConnector)
        terminalPanel.addTerminalPaintListener(MultipleTerminalListener())
        terminalPanel.addTerminalPaintListener(KeywordHighlightPaintListener.getInstance())
        terminalPanel.addTerminalPaintListener(TerminalHyperlinkPaintListener.getInstance())
        terminal.getTerminalModel().setData(Factory, this)

        Disposer.register(terminalPanel, object : Disposable {
            override fun dispose() {
                if (terminal.getTerminalModel().hasData(Factory)) {
                    terminal.getTerminalModel().getData(Factory).removeTerminalPanel(terminalPanel)
                }
            }
        })

        addTerminalPanel(terminalPanel)
        return terminalPanel
    }

    fun getTerminalPanels(): Array<TerminalPanel> {
        return terminalPanels.toTypedArray()
    }

    fun repaintAll() {
        if (SwingUtilities.isEventDispatchThread()) {
            getTerminalPanels().forEach { it.repaintImmediate() }
        } else {
            SwingUtilities.invokeLater { repaintAll() }
        }
    }

    fun fireResize() {
        getTerminalPanels().forEach { c ->
            c.getListeners(ComponentListener::class.java).forEach {
                it.componentResized(ComponentEvent(c, ComponentEvent.COMPONENT_RESIZED))
            }
        }
    }

    fun removeTerminalPanel(terminalPanel: TerminalPanel) {
        terminalPanels.remove(terminalPanel)
    }

    fun addTerminalPanel(terminalPanel: TerminalPanel) {
        terminalPanels.add(terminalPanel)
        terminalPanel.terminal.getTerminalModel().setData(Factory, this)
    }

    private class Painter : Disposable {
        companion object {
            fun getInstance(): Painter {
                return ApplicationScope.forApplicationScope().getOrCreate(Painter::class) { Painter() }
            }
        }

        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        init {
            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    delay(500.milliseconds)
                    SwingUtilities.invokeLater {
                        ApplicationScope.forApplicationScope().windowScopes()
                            .map { getInstance(it) }.forEach { it.repaintAll() }
                    }
                }
            }
        }

        override fun dispose() {
            coroutineScope.cancel()
        }
    }

}