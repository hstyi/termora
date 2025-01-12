package app.termora.keymap

import app.termora.ApplicationScope
import app.termora.actions.TerminalCopyAction
import app.termora.actions.TerminalPasteAction
import app.termora.actions.TerminalSelectAllAction
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class WindowsKeymap private constructor() : Keymap("Windows", KeymapImpl(InputEvent.CTRL_DOWN_MASK), true) {

    companion object {
        fun getInstance(): WindowsKeymap {
            return ApplicationScope.forApplicationScope().getOrCreate(WindowsKeymap::class) { WindowsKeymap() }
        }
    }

    init {

        // Command + C
        super.addShortcut(
            TerminalCopyAction.COPY,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
        )

        // Command + V
        super.addShortcut(
            TerminalPasteAction.PASTE,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
        )


        // Command + A
        super.addShortcut(
            TerminalSelectAllAction.SELECT_ALL,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
        )
    }

    override fun removeAllActionShortcuts(actionId: Any) {
        throw UnsupportedOperationException()
    }

    override fun addShortcut(actionId: String, shortcut: Shortcut) {
        throw UnsupportedOperationException()
    }


}