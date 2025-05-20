package app.termora.db

import app.termora.plugin.Extension

interface DatabaseManagerExtension : Extension {
    fun ready(databaseManager: DatabaseManager)

    /**
     * 数据变动
     */
    fun onDataChanged(id: String, type: DataType, data: String)
}