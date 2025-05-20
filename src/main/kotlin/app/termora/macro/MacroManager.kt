package app.termora.macro

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeleteDataManager
import app.termora.account.AccountManager
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.OwnerType
import org.slf4j.LoggerFactory

/**
 * 宏功能
 */
class MacroManager private constructor() {
    companion object {
        fun getInstance(): MacroManager {
            return ApplicationScope.forApplicationScope().getOrCreate(MacroManager::class) { MacroManager() }
        }

        private val log = LoggerFactory.getLogger(MacroManager::class.java)
    }

    private val macros = mutableMapOf<String, Macro>()
    private val database get() = DatabaseManager.getInstance()


    fun getMacros(): List<Macro> {
        if (macros.isEmpty()) {
            database.data<Macro>(DataType.Macro)
                .forEach { macros[it.id] = it }
        }
        return macros.values.sortedBy { it.created }
    }

    fun addMacro(macro: Macro) {
        macros[macro.id] = macro

        val accountId = AccountManager.getInstance().getAccountId()
        database.save(
            accountId, OwnerType.User, macro.id,
            DataType.Macro, ohMyJson.encodeToString(macro)
        )

        if (log.isDebugEnabled) {
            log.debug("Added macro ${macro.id}")
        }
    }

    fun removeMacro(id: String) {
        database.delete(id)
        macros.remove(id)
        DeleteDataManager.getInstance().removeMacro(id)

        if (log.isDebugEnabled) {
            log.debug("Removed macro $id")
        }
    }
}