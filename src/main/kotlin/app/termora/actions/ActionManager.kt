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
        addAction(Actions.NEW_WINDOW, NewWindowAction())
        addAction(Actions.MULTIPLE, MultipleAction())
        addAction(Actions.APP_UPDATE, AppUpdateAction())
        addAction(Actions.KEYWORD_HIGHLIGHT, KeywordHighlightAction())
        addAction(Actions.SETTING, SettingsAction())
        addAction(Actions.TERMINAL_LOGGER, TerminalLoggerAction())
        addAction(Actions.FIND_EVERYWHERE, FindEverywhereAction())
        addAction(Actions.SFTP, SFTPAction())
        addAction(Actions.MACRO, MacroAction())
        addAction(Actions.KEY_MANAGER, KeyManagerAction())
        addAction(Actions.ADD_HOST, AddHostAction())
        addAction(Actions.OPEN_HOST, OpenHostAction())
    }

    override fun addAction(id: Any, action: Action): Action {
        if (getAction(id) != null) {
            throw IllegalArgumentException("Action already exists")
        }

        return super.addAction(id, action)
    }

}