package app.termora.macro

import app.termora.ApplicationScope
import app.termora.Database
import app.termora.DeleteDataManager
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
    private val database get() = Database.getDatabase()

    init {
        macros.putAll(database.getMacros().associateBy { it.id })
    }

    fun getMacros(): List<Macro> {
        return macros.values.sortedBy { it.created }
    }

    fun addMacro(macro: Macro) {
        database.addMacro(macro)
        macros[macro.id] = macro
        if (log.isDebugEnabled) {
            log.debug("Added macro ${macro.id}")
        }
    }

    fun removeMacro(id: String) {
        database.removeMacro(id)
        macros.remove(id)
        DeleteDataManager.getInstance().removeMacro(id)

        if (log.isDebugEnabled) {
            log.debug("Removed macro $id")
        }
    }
}