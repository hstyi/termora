package app.termora

import app.termora.actions.AnActionEvent
import app.termora.terminal.panel.FloatingToolbarPanel
import org.apache.commons.lang3.StringUtils
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*

abstract class PropertyTerminalTab : TerminalTab {
    protected val listeners = mutableListOf<PropertyChangeListener>()
    var hasFocus = false
        protected set

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        listeners.add(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        listeners.remove(listener)
    }

    protected fun firePropertyChange(event: PropertyChangeEvent) {
        listeners.forEach { l -> l.propertyChange(event) }
    }

    override fun onGrabFocus() {
        hasFocus = true
    }

    override fun onLostFocus() {
        hasFocus = false

        // 切换标签时，尝试隐藏悬浮工具栏
        val evt = AnActionEvent(getJComponent(), StringUtils.EMPTY, EventObject(getJComponent()))
        evt.getData(FloatingToolbarPanel.FloatingToolbar)?.triggerHide()
    }


}