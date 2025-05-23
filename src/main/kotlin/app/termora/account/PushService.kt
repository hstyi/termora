package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.db.Data
import app.termora.db.DatabaseManagerExtension
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.time.Duration.Companion.minutes

/**
 * 同步服务
 */
@Suppress("LoggingSimilarMessage", "DuplicatedCode")
class PushService private constructor() : SyncService(), Disposable, ApplicationRunnerExtension,
    DatabaseManagerExtension {

    companion object {
        private val log = LoggerFactory.getLogger(PushService::class.java)
        val instance by lazy { PushService() }
    }

    /**
     * 多次通知只会生效一次 也就是最后一次
     */
    private val channel = Channel<Unit>(Channel.CONFLATED)
    private val accountManager get() = AccountManager.getInstance()
    private val isFreePlan get() = accountManager.isFreePlan()

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
        try {
            channel.receive()
            synchronize()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }
    }

    private fun synchronize() {
        // 免费方案没有同步
        if (isFreePlan) return

        // 同步
        for (data in getUnsyncedData()) {
            try {
                synchronize(data)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }

    }


    private fun synchronize(data: DataRow) {
        val key = data.data
        val request = Request.Builder().url("${accountManager.getServer()}/v1/data/push")
            .post(ohMyJson.encodeToString(data).toRequestBody("application/json".toMediaType()))
            .build()
        val response = AccountHttp.client.newCall(request).execute()

        val text = response.use { response.body.use { it?.string() } }
        if (text.isNullOrBlank()) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        // 如果是 403 ，说明没有权限
        if (response.code == 403) {
            if (log.isWarnEnabled) {
                log.warn("Data.id {} No permission to push", data.id)
            }
            // 标记为已经同步
            updateData(data.id, synced = true, version = data.version)
            return
        } else if (response.code == 409) { // 版本冲突，一般来说是云端版本大于本地版本
            val json = ohMyJson.decodeFromString<JsonObject>(text)
            // 最新版
            val version = json["data"]?.jsonObject?.get("version")?.jsonPrimitive?.long
            if (version == null) {
                if (log.isWarnEnabled) {
                    log.warn("Data.id {} conflict, last version is null", data.id)
                }
            }
            // 通知拉取
            PullService.instance.trigger(data.id)
            return
        }

        // 没有错误就是推送成功了
        if (response.isSuccessful.not()) {
            throw ResponseException(response.code, response)
        }

        if (log.isInfoEnabled) {
            log.info("Data.id {} pushed into cloud", data.id)
        }


    }

    override fun ready() {
        // 开始工作
        run()
    }

    override fun onDataChanged(data: Data) {
        channel.trySend(Unit).isSuccess
    }

}