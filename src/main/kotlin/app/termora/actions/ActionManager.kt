package app.termora.actions

import app.termora.Actions
import app.termora.ApplicationScope
import app.termora.findeverywhere.FindEverywhereAction
import app.termora.highlight.KeywordHighlightAction
import app.termora.keymgr.KeyManagerAction
import app.termora.macro.MacroAction
import app.termora.tlog.TerminalLoggerAction
import app.termora.transport.SFTPAction
import javax.swing.Action

class ActionManager : org.jdesktop.swingx.action.ActionManager() {

    companion object {
        fun getInstance(): ActionManager {
            return ApplicationScope.forApplicationScope().getOrCreate(ActionManager::class) { ActionManager() }
        }
    }

    init {
        setInstance(this)
        registerActions()
    }


    private fun registerActions() {
        addAction(NewWindowAction.NEW_WINDOW, NewWindowAction())
        addAction(FindEverywhereAction.FIND_EVERYWHERE, FindEverywhereAction())

        addAction(Actions.MULTIPLE, MultipleAction())
        addAction(Actions.APP_UPDATE, AppUpdateAction())
        addAction(Actions.KEYWORD_HIGHLIGHT, KeywordHighlightAction())
        addAction(Actions.TERMINAL_LOGGER, TerminalLoggerAction())
        addAction(Actions.SFTP, SFTPAction())
        addAction(SFTPCommandAction.SFTP_COMMAND, SFTPCommandAction())
        addAction(Actions.MACRO, MacroAction())
        addAction(Actions.KEY_MANAGER, KeyManagerAction())

        addAction(SwitchTabAction.SWITCH_TAB, SwitchTabAction())
        addAction(TabReconnectAction.RECONNECT_TAB, TabReconnectAction())
        addAction(SettingsAction.SETTING, SettingsAction())

        addAction(NewHostAction.NEW_HOST, NewHostAction())
        addAction(OpenHostAction.OPEN_HOST, OpenHostAction())

        addAction(TerminalCopyAction.COPY, TerminalCopyAction())
        addAction(TerminalPasteAction.PASTE, TerminalPasteAction())
        addAction(TerminalFindAction.FIND, TerminalFindAction())
        addAction(TerminalCloseAction.CLOSE, TerminalCloseAction())
        addAction(TerminalClearScreenAction.CLEAR_SCREEN, TerminalClearScreenAction())
        addAction(OpenLocalTerminalAction.LOCAL_TERMINAL, OpenLocalTerminalAction())
        addAction(TerminalSelectAllAction.SELECT_ALL, TerminalSelectAllAction())

        addAction(TerminalZoomInAction.ZOOM_IN, TerminalZoomInAction())
        addAction(TerminalZoomOutAction.ZOOM_OUT, TerminalZoomOutAction())
        addAction(TerminalZoomResetAction.ZOOM_RESET, TerminalZoomResetAction())
    }

    override fun addAction(action: Action): Action {
        val actionId = action.getValue(Action.ACTION_COMMAND_KEY) ?: throw IllegalArgumentException("Invalid action ID")
        return addAction(actionId, action)
    }

    override fun addAction(id: Any, action: Action): Action {
        if (getAction(id) != null) {
            throw IllegalArgumentException("Action already exists")
        }

        return super.addAction(id, action)
    }

}