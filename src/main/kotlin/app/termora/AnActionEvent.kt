package app.termora

import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.ActionEvent
import java.util.*

open class AnActionEvent(
    source: Any, id: Int, command: String,
    val event: EventObject
) : ActionEvent(source, id, command) {

    private fun getSourceWindow(): Window? {
        var window: Window? = null
        if (source is Component) {
            window = ApplicationScope.getFrameForComponent(source as Component)
        }
        return window
    }

    val window: Window get() = getSourceWindow() ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow

    /**
     * 根据 [source] 获取域，如果获取失败，那么返回全局的
     */
    val scope: Scope
        get() = getSourceWindow()?.let { ApplicationScope.forWindowScope(it) } ?: ApplicationScope.forApplicationScope()
}