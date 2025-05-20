package app.termora.db

import app.termora.plugin.Extension

interface DatabaseManagerExtension : Extension {
    fun ready(databaseManager: DatabaseManager)
}