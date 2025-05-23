package app.termora.db

import app.termora.plugin.Extension

interface DatabaseManagerExtension : Extension {
    /**
     * 数据库初始化完成
     */
    fun ready(databaseManager: DatabaseManager) {}

    /**
     * 数据变动
     */
    fun onDataChanged(data: Data) {}
}