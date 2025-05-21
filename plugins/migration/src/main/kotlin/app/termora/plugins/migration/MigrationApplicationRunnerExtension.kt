package app.termora.plugins.migration

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.OwnerType
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

class MigrationApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        private val log = LoggerFactory.getLogger(MigrationApplicationRunnerExtension::class.java)
        val instance by lazy { MigrationApplicationRunnerExtension() }
    }

    override fun ready() {
        val file = getDatabaseFile()
        if (file.exists().not()) return

        // 如果数据库文件存在，那么需要迁移文件
        val countDownLatch = CountDownLatch(1)

        SwingUtilities.invokeAndWait {
            try {
                // 打开数据
                openDatabase()

                // 尝试解锁
                openDoor()

                // 询问是否迁移
                if (askMigrate()) {

                    // 迁移
                    migrate()

                    // 移动到旧的目录
                    moveOldDirectory()

                    // 重启
                    restart()

                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            } finally {
                countDownLatch.countDown()
            }

        }

        countDownLatch.await()

    }

    private fun openDoor() {
        if (Doorman.getInstance().isWorking()) {
            if (DoormanDialog(null).open().not()) {
                Disposer.dispose(TermoraFrameManager.getInstance())
            }
        }
    }

    private fun openDatabase() {
        try {
            // 初始化数据库
            Database.getDatabase()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            JOptionPane.showMessageDialog(
                null, "Unable to open database",
                I18n.getString("termora.title"), JOptionPane.ERROR_MESSAGE
            )
            exitProcess(1)
        }
    }

    private fun migrate() {
        val database = Database.getDatabase()
        val accountManager = AccountManager.getInstance()
        val databaseManager = DatabaseManager.getInstance()
        val ownerId = accountManager.getAccountId()

        for (host in database.getHosts()) {
            if (host.deleted) continue
            databaseManager.save(
                ownerId, OwnerType.User, host.id,
                DataType.Host, ohMyJson.encodeToString(host)
            )
        }

        for (snippet in database.getSnippets()) {
            if (snippet.deleted) continue
            databaseManager.save(
                ownerId, OwnerType.User, snippet.id,
                DataType.Snippet, ohMyJson.encodeToString(snippet)
            )
        }

        for (macro in database.getMacros()) {
            databaseManager.save(
                ownerId, OwnerType.User, macro.id,
                DataType.Macro, ohMyJson.encodeToString(macro)
            )
        }

        for (keymap in database.getKeymaps()) {
            databaseManager.save(
                ownerId, OwnerType.User, keymap.id,
                DataType.Keymap, keymap.toJSON()
            )
        }

        for (keypair in database.getKeyPairs()) {
            databaseManager.save(
                ownerId, OwnerType.User, keypair.id,
                DataType.KeyPair, ohMyJson.encodeToString(keypair)
            )
        }

        for (e in database.getKeywordHighlights()) {
            databaseManager.save(
                ownerId, OwnerType.User, e.id,
                DataType.KeywordHighlight, ohMyJson.encodeToString(e)
            )
        }

        val list = listOf(
            database.sync,
            database.properties,
            database.terminal,
            database.sftp,
            database.appearance,
        )

        for (e in list) {
            for (k in e.getProperties()) {
                databaseManager.setSetting(e.name + "." + k.key, k.value)
            }
        }

        for (e in database.safetyProperties.getProperties()) {
            databaseManager.setSetting(database.properties.name + "." + e.key, e.value)
        }


    }

    private fun askMigrate(): Boolean {

        if (MigrationDialog(null).open()) {
            return true
        }

        // 移动到旧的目录
        moveOldDirectory()

        // 重启
        restart()

        return false
    }

    private fun moveOldDirectory() {
        // 关闭数据库
        Disposer.dispose(Database.getDatabase())

        val file = getDatabaseFile()
        FileUtils.moveDirectory(
            file,
            FileUtils.getFile(file.parentFile, file.name + "-old-" + System.currentTimeMillis())
        )

    }

    private fun restart() {

        // 重启
        TermoraRestarter.getInstance().scheduleRestart(null, ask = false)

        // 退出程序
        Disposer.dispose(TermoraFrameManager.getInstance())
    }


    fun getDatabaseFile(): File {
        return FileUtils.getFile(Application.getBaseDataDir(), "storage")
    }
}