package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.db.Data
import app.termora.db.DatabaseManagerExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
        val list = getUnsyncedData()
        for (data in list) {
            try {
                synchronize(data)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }

    }


    private fun synchronize(data: Data) {
        if (data.deleted) {
            delete(data)
        } else {
            push(data)
        }
    }

    private fun delete(data: Data) {
        val request = Request.Builder().url("${accountManager.getServer()}/v1/data/${data.id}")
            .delete()
            .build()

        AccountHttp.execute(request = request)

        // 修改为已经同步
        updateData(data.id, synced = true)

        if (log.isInfoEnabled) {
            log.info("{} has been deleted from the cloud", data.id)
        }
    }

    private fun push(data: Data) {
        val iv = DigestUtils.sha256(data.id).copyOf(12)
        val requestData = PushDataRequest(
            objectId = data.id,
            ownerId = data.ownerId,
            ownerType = data.ownerType,
            version = data.version,
            type = data.type,
            data = Base64.encodeBase64String(
                AES.GCM.encrypt(
                    accountManager.getSecretKey(), iv,
                    data.data.toByteArray()
                )
            )
        )
        val request = Request.Builder().url("${accountManager.getServer()}/v1/data/push")
            .post(ohMyJson.encodeToString(requestData).toRequestBody("application/json".toMediaType()))
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

        // 修改为已经同步
        updateData(data.id, synced = true, version = data.version)

        if (log.isInfoEnabled) {
            log.info("Data.id {} pushed into cloud", data.id)
        }

    }

    override fun ready() {

        // 同步
        swingCoroutineScope.launch(Dispatchers.IO) { while (isActive) schedule() }

        // 定时同步
        swingCoroutineScope.launch(Dispatchers.IO) {
            delay(10.seconds)
            while (isActive) {
                // 发送同步
                channel.send(Unit)
                // 每 1 分钟尝试同步一次，除非收到数据变动通知
                delay(1.minutes)
            }
        }

    }

    override fun onDataChanged(id: String, type: String) {
        channel.trySend(Unit).isSuccess
    }


    @Serializable
    private data class PushDataRequest(
        val objectId: String,
        val ownerId: String,
        val ownerType: String,
        val version: Long,
        val type: String,
        val data: String
    )
}