package app.termora.keymgr

import app.termora.ApplicationScope
import app.termora.Database
import app.termora.DeleteDataManager

class KeyManager private constructor() {
    companion object {
        fun getInstance(): KeyManager {
            return ApplicationScope.forApplicationScope().getOrCreate(KeyManager::class) { KeyManager() }
        }
    }

    private val keyPairs = mutableSetOf<OhKeyPair>()
    private val database get() = Database.getDatabase()

    init {
        keyPairs.addAll(database.getKeyPairs())
    }

    fun addOhKeyPair(keyPair: OhKeyPair) {
        if (keyPair == OhKeyPair.empty) {
            return
        }
        keyPairs.remove(keyPair)
        keyPairs.add(keyPair)
        database.addKeyPair(keyPair)
    }

    fun removeOhKeyPair(id: String) {
        keyPairs.removeIf { it.id == id }
        database.removeKeyPair(id)
        DeleteDataManager.getInstance().removeKeyPair(id)
    }

    fun getOhKeyPairs(): List<OhKeyPair> {
        return keyPairs.sortedBy { it.sort }
    }

    fun getOhKeyPair(id: String): OhKeyPair? {
        return keyPairs.findLast { it.id == id }
    }

}