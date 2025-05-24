package app.termora.db

import app.termora.db.DatabaseManager.Companion.log
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager
import javax.swing.SwingUtilities

interface DatabaseManagerExtension : Extension {

    companion object {
        fun fireDataChanged(id: String, type: String) {
            if (SwingUtilities.isEventDispatchThread()) {
                for (extension in ExtensionManager.getInstance().getExtensions(DatabaseManagerExtension::class.java)) {
                    try {
                        extension.onDataChanged(id, type)
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            } else {
                SwingUtilities.invokeLater { fireDataChanged(id, type) }
            }
        }
    }

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