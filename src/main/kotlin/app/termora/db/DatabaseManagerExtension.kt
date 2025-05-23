package app.termora.db

import app.termora.plugin.Extension

interface DatabaseManagerExtension : Extension {
    /**
     * 数据库初始化完成
     */
    fun ready(databaseManager: DatabaseManager) {}

    /**
     * 数据变动
     *
     * @param type 为空时表示删除
     */
    fun onDataChanged(id: String, type: String) {}
}