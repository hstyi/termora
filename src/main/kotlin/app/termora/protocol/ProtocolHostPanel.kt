package app.termora.protocol

import app.termora.Host
import app.termora.SwingUtils
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import javax.swing.JPanel
import javax.swing.SwingUtilities

abstract class ProtocolHostPanel : JPanel(BorderLayout()) {

    private var lastFocusOwner: Component? = null

    /**
     * 获取 Host
     */
    abstract fun getHost(): Host

    /**
     * 验证字段
     */
    abstract fun validateFields(): Boolean

    /**
     * 隐藏之前
     */
    open fun onBeforeHidden() {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return
        if (SwingUtilities.isDescendingFrom(owner, this)) {
            lastFocusOwner = owner
        }
    }

    /**
     * 隐藏之后
     */
    open fun onHidden() {}

    /**
     * 显示之前
     */
    open fun onBeforeShown() {}

    /**
     * 显示之后
     */
    open fun onShown() {
        lastFocusOwner?.requestFocusInWindow()
    }
}