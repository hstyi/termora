package app.termora.keymap

import app.termora.I18n
import app.termora.actions.*
import app.termora.findeverywhere.FindEverywhereAction
import app.termora.keymap.KeyShortcut.Companion.toHumanText
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import org.jdesktop.swingx.action.BoundAction.ACTION_COMMAND_KEY
import javax.swing.Action
import javax.swing.table.DefaultTableModel

class KeymapTableModel : DefaultTableModel() {

    private val actionManager get() = ActionManager.getInstance()
    private val keymapManager get() = KeymapManager.getInstance()


    init {
        for (id in listOf(
            TerminalCopyAction.COPY,
            TerminalPasteAction.PASTE,
            TerminalSelectAllAction.SELECT_ALL,
            TerminalFindAction.FIND,
            TerminalCloseAction.CLOSE,
            TerminalZoomInAction.ZOOM_IN,
            TerminalZoomOutAction.ZOOM_OUT,
            TerminalZoomResetAction.ZOOM_RESET,
            OpenLocalTerminalAction.LOCAL_TERMINAL,
            TerminalClearScreenAction.CLEAR_SCREEN,
            FindEverywhereAction.FIND_EVERYWHERE,
            NewWindowAction.NEW_WINDOW,
            TabReconnectAction.RECONNECT_TAB,
            SwitchTabAction.SWITCH_TAB,
        )) {
            val action = actionManager.getAction(id) ?: continue
            super.addRow(arrayOf(action))
        }
    }

    override fun getColumnCount(): Int {
        return 2
    }

    override fun getColumnName(column: Int): String {
        return if (column == 0) I18n.getString("termora.settings.keymap.shortcut")
        else I18n.getString("termora.settings.keymap.action")
    }

    fun getAction(row: Int): Action? {
        return super.getValueAt(row, 0) as Action?
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val action = getAction(row) ?: return StringUtils.EMPTY
        if (column == 0) {
            val actionId = action.getValue(ACTION_COMMAND_KEY) ?: StringUtils.EMPTY
            val shortcuts = keymapManager.getActiveKeymap().getShortcut(actionId)
            if (shortcuts.isNotEmpty()) {
                val keyShortcut = shortcuts.first()
                if (keyShortcut is KeyShortcut) {
                    if (actionId == SwitchTabAction.SWITCH_TAB) {
                        return toHumanText(keyShortcut.keyStroke) + " .. 9"
                    }
                    return toHumanText(keyShortcut.keyStroke)
                }
            }
        } else if (column == 1) {
            return action.getValue(Action.SHORT_DESCRIPTION)
                ?: action.getValue(Action.NAME) ?: StringUtils.EMPTY
        }
        return StringUtils.EMPTY
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }


}