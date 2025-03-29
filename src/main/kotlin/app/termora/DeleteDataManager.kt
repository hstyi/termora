package app.termora

/**
 * 仅标记
 */
class DeleteDataManager private constructor() {
    companion object {
        fun getInstance(): DeleteDataManager {
            return ApplicationScope.forApplicationScope().getOrCreate(DeleteDataManager::class) { DeleteDataManager() }
        }
    }

    private val data = mutableMapOf<String, DeletedData>()
    private val database get() = Database.getDatabase()

    fun removeHost(id: String, deleteDate: Long = System.currentTimeMillis()) {
        addDeletedData(DeletedData(id, "Host", deleteDate))
    }

    fun removeKeymap(id: String, deleteDate: Long = System.currentTimeMillis()) {
        addDeletedData(DeletedData(id, "Keymap", deleteDate))
    }

    fun removeKeyPair(id: String, deleteDate: Long = System.currentTimeMillis()) {
        addDeletedData(DeletedData(id, "KeyPair", deleteDate))
    }

    fun removeKeywordHighlight(id: String, deleteDate: Long = System.currentTimeMillis()) {
        addDeletedData(DeletedData(id, "KeywordHighlight", deleteDate))
    }

    fun removeMacro(id: String, deleteDate: Long = System.currentTimeMillis()) {
        addDeletedData(DeletedData(id, "Macro", deleteDate))
    }

    fun removeSnippet(id: String, deleteDate: Long = System.currentTimeMillis()) {
        addDeletedData(DeletedData(id, "Snippet", deleteDate))
    }

    private fun addDeletedData(deletedData: DeletedData) {
        if (data.containsKey(deletedData.id)) return
        data[deletedData.id] = deletedData
        database.addDeletedData(deletedData)
    }

    fun getDeletedData(): List<DeletedData> {
        if (data.isEmpty()) {
            data.putAll(database.getDeletedData().associateBy { it.id })
        }
        return data.values.sortedBy { it.deleteDate }
    }
}