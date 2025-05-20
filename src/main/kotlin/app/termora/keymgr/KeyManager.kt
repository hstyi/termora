package app.termora.keymgr

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeleteDataManager
import app.termora.account.AccountManager
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.OwnerType

class KeyManager private constructor() {
    companion object {
        fun getInstance(): KeyManager {
            return ApplicationScope.forApplicationScope().getOrCreate(KeyManager::class) { KeyManager() }
        }
    }

    private val keyPairs = mutableSetOf<OhKeyPair>()
    private val database get() = DatabaseManager.getInstance()


    fun addOhKeyPair(keyPair: OhKeyPair) {
        if (keyPair == OhKeyPair.empty) {
            return
        }

        keyPairs.remove(keyPair)
        keyPairs.add(keyPair)

        val accountId = AccountManager.getInstance().getAccountId()
        database.save(
            accountId, OwnerType.User, keyPair.id,
            DataType.KeyPair, ohMyJson.encodeToString(keyPair)
        )
    }

    fun removeOhKeyPair(id: String) {
        keyPairs.removeIf { it.id == id }
        database.delete(id)
        DeleteDataManager.getInstance().removeKeyPair(id)
    }

    fun getOhKeyPairs(): List<OhKeyPair> {
        if (keyPairs.isEmpty()) {
            keyPairs.addAll(database.data<OhKeyPair>(DataType.KeyPair))
        }
        return keyPairs.sortedBy { it.sort }
    }

    fun getOhKeyPair(id: String): OhKeyPair? {
        return keyPairs.findLast { it.id == id }
    }

}