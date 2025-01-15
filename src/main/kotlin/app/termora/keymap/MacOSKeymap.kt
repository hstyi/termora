package app.termora.keymap

import app.termora.ApplicationScope
import app.termora.actions.TerminalCopyAction
import app.termora.actions.TerminalPasteAction
import app.termora.actions.TerminalSelectAllAction
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class MacOSKeymap private constructor() : Keymap("macOS", KeymapImpl(InputEvent.META_DOWN_MASK), true) {

    companion object {
        fun getInstance(): MacOSKeymap {
            return ApplicationScope.forApplicationScope().getOrCreate(MacOSKeymap::class) { MacOSKeymap() }
        }
    }

    init {

        // Command + C
        super.addShortcut(
            TerminalCopyAction.COPY,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK))
        )

        // Command + V
        super.addShortcut(
            TerminalPasteAction.PASTE,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK))
        )


        // Command + A
        super.addShortcut(
            TerminalSelectAllAction.SELECT_ALL,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.META_DOWN_MASK))
        )
    }

    override fun removeAllActionShortcuts(actionId: Any) {
        throw UnsupportedOperationException()
    }

    override fun addShortcut(actionId: String, shortcut: Shortcut) {
        throw UnsupportedOperationException()
    }


}