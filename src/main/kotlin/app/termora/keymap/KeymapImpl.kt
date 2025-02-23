package app.termora.keymap

import app.termora.actions.*
import app.termora.findeverywhere.FindEverywhereAction
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class KeymapImpl(private val menuShortcutKeyMaskEx: Int) : Keymap("Keymap", null, true) {

    init {
        this.registerShortcuts()
    }


    private fun registerShortcuts() {

        // new window
        addShortcut(
            NewWindowAction.NEW_WINDOW,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuShortcutKeyMaskEx))
        )

        // Find Everywhere
        addShortcut(
            FindEverywhereAction.FIND_EVERYWHERE,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_T, menuShortcutKeyMaskEx))
        )

        // Command + L
        addShortcut(
            OpenLocalTerminalAction.LOCAL_TERMINAL,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_L, menuShortcutKeyMaskEx))
        )


        // Command + L
        addShortcut(
            TerminalFindAction.FIND,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F, menuShortcutKeyMaskEx))
        )

        // Command + W
        addShortcut(
            TerminalCloseAction.CLOSE,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuShortcutKeyMaskEx))
        )

        // Command + Shift + L
        addShortcut(
            TerminalClearScreenAction.CLEAR_SCREEN,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_L, menuShortcutKeyMaskEx or InputEvent.SHIFT_DOWN_MASK))
        )

        // Command + +
        addShortcut(
            TerminalZoomInAction.ZOOM_IN,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuShortcutKeyMaskEx))
        )

        // Command + -
        addShortcut(
            TerminalZoomOutAction.ZOOM_OUT,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuShortcutKeyMaskEx))
        )

        // Command + 0
        addShortcut(
            TerminalZoomResetAction.ZOOM_RESET,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_0, menuShortcutKeyMaskEx))
        )

        // Command + Shift + R
        addShortcut(
            TabReconnectAction.RECONNECT_TAB,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_R, menuShortcutKeyMaskEx or InputEvent.SHIFT_DOWN_MASK))
        )

        // Command + Shift + P
        addShortcut(
            SFTPCommandAction.SFTP_COMMAND,
            KeyShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_P, menuShortcutKeyMaskEx or InputEvent.SHIFT_DOWN_MASK))
        )


        // switch map
        for (i in KeyEvent.VK_1..KeyEvent.VK_9) {
            addShortcut(
                SwitchTabAction.SWITCH_TAB,
                KeyShortcut(KeyStroke.getKeyStroke(i, menuShortcutKeyMaskEx))
            )
        }

    }
}