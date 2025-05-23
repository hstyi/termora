package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.time.Duration.Companion.minutes

/**
 * 同步服务
 */
@Suppress("LoggingSimilarMessage", "DuplicatedCode")
class PullService private constructor() : SyncService(), Disposable, ApplicationRunnerExtension {

    companion object {
        private val log = LoggerFactory.getLogger(PullService::class.java)
        val instance by lazy { PullService() }
    }

    /**
     * 多次通知只会生效一次 也就是最后一次
     */
    private val channel = Channel<String>(Channel.UNLIMITED)
    private val accountManager get() = AccountManager.getInstance()
    private val accountProperties get() = AccountProperties.getInstance()

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
                                try {
                                    // 拉取变动的
                                    pullChanges()
                                } catch (e: Exception) {
                                    if (log.isErrorEnabled) {
                                        log.error(e.message, e)
                                    }
                                }
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

    private fun pullChanges() {
        val lastSynchronizationOn = accountProperties.lastSynchronizationOn

    }

    private suspend fun synchronize() {


    }


    private fun pull(id: String) {
        val request = Request.Builder().url("${accountManager.getServer()}/v1/data/${id}")
            .get()
            .build()
        val response = AccountHttp.client.newCall(request).execute()

        // 云端数据不存在直接返回
        if (response.code == 404) {
            if (log.isWarnEnabled) {
                log.warn("The data {} does not exist in the cloud", id)
            }
            return
        }

        // 没有权限拉取
        if (response.code == 403) {
            if (log.isWarnEnabled) {
                log.warn("Data.id {} No permission to push", id)
            }
            return
        }

        // 其他错误
        if (response.isSuccessful.not()) {
            throw ResponseException(response.code, response)
        }

        val text = response.use { response.body.use { it?.string() } }
        if (text.isNullOrBlank()) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        val json = ohMyJson.decodeFromString<JsonObject>(text)
        val id = json["id"]?.jsonPrimitive?.content
        val ownerId = json["ownerId"]?.jsonPrimitive?.content
        val ownerType = json["ownerType"]?.jsonPrimitive?.content
        val data = json["data"]?.jsonPrimitive?.content
        val type = json["type"]?.jsonPrimitive?.content
        val version = json["version"]?.jsonPrimitive?.long
        val deleted = json["deleted"]?.jsonPrimitive?.boolean
        if (id == null || ownerId == null || ownerType == null || data == null || version == null || deleted == null || type == null) {
            throw IllegalStateException("Data.id $id data error")
        }

        val row = getData(id)

        // 如果本地不存在，
        if (row == null) {
            // 云端已经删除，那么忽略
            if (deleted) return
            // 保存到本地数据库
//            databaseManager.save(ownerId, ownerType, id, type, data, version = version, synced = true)
            return
        }

        // 如果本地版本大于云端版本，那么忽略，因为需要同步
        if (row.version > version) {
            // 如果没有同步标识，那么修改为代同步
            if (row.synced.not()) {
                updateData(id, false, row.version)
            }
            return
        }

        // 本地版本小于云端版本，那么立即修改
        if (row.version < version) {
            // 保存
//            databaseManager.save(ownerId, ownerType, id, type, data, version = version, synced = true)
        }

    }

    fun trigger(id: String) {
        channel.trySend(id).isSuccess
    }


    override fun ready() {
        // 开始工作
        run()
    }


}