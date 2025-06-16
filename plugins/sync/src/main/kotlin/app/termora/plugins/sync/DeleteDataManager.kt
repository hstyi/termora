package app.termora.plugins.sync

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeletedData
import app.termora.EnableManager
import app.termora.database.DatabaseChangedExtension
import app.termora.database.DatabaseManager

/**
 * 仅标记
 */
class DeleteDataManager private constructor() : DatabaseChangedExtension {
    companion object {
        fun getInstance(): DeleteDataManager {
            return ApplicationScope.Companion.forApplicationScope()
                .getOrCreate(DeleteDataManager::class) { DeleteDataManager() }
        }
    }

    private val data = mutableMapOf<String, DeletedData>()
    private val databaseManager get() = DatabaseManager.Companion.getInstance()
    private val enableManager get() = EnableManager.Companion.getInstance()

    init {
        for (e in databaseManager.properties.getProperties()) {
            if (e.key.startsWith("Setting.Properties.DeleteData_")) {
                val deletedData = runCatching { ohMyJson.decodeFromString<DeletedData>(e.value) }
                    .getOrNull() ?: continue
                data[deletedData.id] = deletedData
            }
        }
    }

    private fun addDeletedData(deletedData: DeletedData) {
        data[deletedData.id] = deletedData
    }

    fun getDeletedData(): List<DeletedData> {
        return data.values.sortedBy { it.deleteDate }
    }


    override fun onDataChanged(
        id: String,
        type: String,
        action: DatabaseChangedExtension.Action,
        source: DatabaseChangedExtension.Source
    ) {
        if (action != DatabaseChangedExtension.Action.Removed) return
        if (id.isBlank() || type.isBlank()) return
        val key = "DeleteData_${type}_${id}"
        val deletedData = DeletedData(id, type, System.currentTimeMillis())
        enableManager.setFlag(key, ohMyJson.encodeToString(deletedData))
        addDeletedData(deletedData)
    }
}