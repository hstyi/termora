package app.termora.shortcut

import app.termora.Actions
import app.termora.ApplicationScope
import app.termora.Disposable
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class KeyShortcutsManager private constructor() : Disposable {

    companion object {
        fun getInstance(): KeyShortcutsManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeyShortcutsManager::class) { KeyShortcutsManager() }
        }
    }

    private val keyEventDispatcher = MyKeyEventDispatcher()
    private val shortcuts = mutableMapOf<Shortcut, Any>()

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(keyEventDispatcher)

        // Register Shortcuts
        registerShortcuts()
    }


    fun addShortcut(actionId: Any, keyShortcut: KeyShortcut) {
        shortcuts[keyShortcut] = actionId
    }

    private inner class MyKeyEventDispatcher : KeyEventDispatcher {
        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            val actionId = shortcuts[KeyShortcut(KeyStroke.getKeyStrokeForEvent(e))] ?: return false
            val action = ActionManager.getInstance().getAction(actionId)
                ?: ActionManager.getInstance().getAction(actionId) ?: return false

            if (action.isEnabled) {
                action.actionPerformed(ActionEvent(e.source, ActionEvent.ACTION_PERFORMED, StringUtils.EMPTY))
            }

            return false
        }

    }


    private fun registerShortcuts() {

        // new window
        addShortcut(
            Actions.NEW_WINDOW,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx))
        )

        // Find Everywhere
        addShortcut(
            Actions.FIND_EVERYWHERE,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_T, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx))
        )

    }

    override fun dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .removeKeyEventDispatcher(keyEventDispatcher)
    }
}