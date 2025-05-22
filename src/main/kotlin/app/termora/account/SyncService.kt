package app.termora.account

import app.termora.*
import app.termora.db.Data
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.DatabaseManagerExtension
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.minutes

/**
 * 同步服务
 */
class SyncService private constructor() : Disposable, ApplicationRunnerExtension, DatabaseManagerExtension {

    companion object {
        private val log = LoggerFactory.getLogger(SyncService::class.java)
        val instance by lazy { SyncService() }
    }

    /**
     * 多次通知只会生效一次 也就是最后一次
     */
    private val channel = Channel<Unit>(Channel.CONFLATED)
    private val database get() = DatabaseManager.getInstance().database
    private val lock get() = DatabaseManager.getInstance().lock
    private val isFreePlan get() = AccountManager.getInstance().isFreePlan()

    private fun run() {

        // 同步
        swingCoroutineScope.launch(Dispatchers.IO) { while (isActive) schedule() }

        // 在 Frame 显示后再开始同步
        DynamicExtensionHandler.getInstance().register(FrameExtension::class.java, object : FrameExtension {
            override fun customize(frame: TermoraFrame) {
                DynamicExtensionHandler.getInstance().unregister(this)
                frame.addWindowListener(object : WindowAdapter() {
                    override fun windowOpened(e: WindowEvent) {
                        frame.removeWindowListener(this)
                        // 定时同步
                        swingCoroutineScope.launch(Dispatchers.IO) {
                            while (isActive) {
                                // 发送同步
                                channel.send(Unit)
                                // 每 1 分钟尝试同步一次，除非收到数据变动通知
                                delay(1.minutes)
                            }
                        }
                    }
                })
            }
        })

    }

    private suspend fun schedule() {
        channel.receive()

        try {
            // 同步
            synchronize()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }
    }

    private suspend fun synchronize() {
        // 免费方案没有同步
        if (isFreePlan) return

        val list = getUnsyncedData()
        if (list.isEmpty()) return

        println(list)
    }

    private fun getUnsyncedData(): List<Pair<DataType, String>> {
        val list = mutableListOf<Pair<DataType, String>>()
        lock.withLock {
            transaction(database) {
                val rows = Data.selectAll().where { (Data.synced eq false) }.toList()
                for (row in rows) {
                    try {
                        val type = runCatching { DataType.valueOf(row[Data.type]) }.getOrNull() ?: continue
                        list.add(type to row[Data.data])
                    } catch (e: Exception) {
                        if (DatabaseManager.Companion.log.isWarnEnabled) {
                            DatabaseManager.Companion.log.warn(e.message, e)
                        }
                    }
                }
            }
        }
        return list
    }

    override fun ready() {
        // 开始工作
        run()
    }

    override fun onDataChanged(id: String, type: DataType, data: String) {
        // 数据变动
        channel.trySend(Unit).isSuccess
    }

}