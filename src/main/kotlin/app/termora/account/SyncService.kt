package app.termora.account

import app.termora.ApplicationRunnerExtension
import app.termora.Disposable
import app.termora.db.DataType
import app.termora.db.DatabaseManagerExtension
import app.termora.swingCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
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

    private fun run() {

        // 定时同步
        swingCoroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                // 发送同步
                channel.send(Unit)
                // 每 1 分钟尝试同步一次，除非收到数据变动通知
                delay(1.minutes)
            }
        }

        // 同步
        swingCoroutineScope.launch(Dispatchers.IO) { while (isActive) schedule() }

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