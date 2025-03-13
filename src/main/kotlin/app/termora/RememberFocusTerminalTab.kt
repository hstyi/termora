package app.termora

import java.awt.Component
import java.awt.KeyboardFocusManager

abstract class RememberFocusTerminalTab : TerminalTab {
    private var lastFocusedComponent: Component? = null

    override fun onLostFocus() {
        lastFocusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    }

    override fun onGrabFocus() {
        lastFocusedComponent?.requestFocusInWindow()
    }
}