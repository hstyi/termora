package app.termora.plugins.sync

import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.DatabaseManagerExtension
import app.termora.sync.SyncManager

class SyncDatabaseManagerExtension : DatabaseManagerExtension {
    companion object {
        val instance by lazy { SyncDatabaseManagerExtension() }
    }

    override fun ready(databaseManager: DatabaseManager) {

    }

    override fun onDataChanged(id: String, type: DataType, data: String) {
        SyncManager.getInstance().triggerOnChanged()
    }
}