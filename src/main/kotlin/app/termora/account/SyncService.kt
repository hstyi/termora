package app.termora.account

import app.termora.db.Data
import app.termora.db.Data.Companion.toData
import app.termora.db.DataEntity
import app.termora.db.DatabaseManager
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.concurrent.withLock

abstract class SyncService {

    protected val databaseManager get() = DatabaseManager.getInstance()
    protected val database get() = databaseManager.database
    protected val lock get() = databaseManager.lock

    protected fun getData(id: String): Data? {
        val list = mutableListOf<Data>()
        lock.withLock {
            transaction(database) {
                val rows = DataEntity.selectAll().where { (DataEntity.id.eq(id)) }.toList()
                for (row in rows) {
                    list.add(row.toData())
                }
            }
        }
        return list.firstOrNull()
    }


    protected fun updateData(id: String, synced: Boolean, version: Long? = null) {
        lock.withLock {
            transaction(database) {
                DataEntity.update({ DataEntity.id.eq(id) }) {
                    if (version != null) it[DataEntity.version] = version
                    it[DataEntity.synced] = synced
                }
            }
        }
    }


    protected fun getUnsyncedData(): List<Data> {
        val list = mutableListOf<Data>()
        lock.withLock {
            transaction(database) {
                val rows = DataEntity.selectAll().where { (DataEntity.synced eq false) }.toList()
                for (row in rows) {
                    list.add(row.toData())
                }
            }
        }
        return list
    }
}