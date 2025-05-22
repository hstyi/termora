package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.Icons.server
import app.termora.plugin.ExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringUtils
import java.security.PrivateKey
import java.security.PublicKey
import javax.swing.SwingUtilities

class AccountManager private constructor() : ApplicationRunnerExtension {
    companion object {
        fun getInstance(): AccountManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(AccountManager::class) { AccountManager() }
        }
    }

    private var account = locally()
    fun getAccountId() = account.id
    fun getServer() = account.server
    fun getEmail() = account.email
    fun getSubscriptions() = account.subscriptions
    fun getTeams() = account.teams
    fun getSecretKey() = account.secretKey
    fun getPublicKey() = account.publicKey
    fun getPrivateKey() = account.privateKey
    fun isLocally() = account.isLocally
    fun getLastSynchronizationOn() = account.lastSynchronizationOn
    fun getAccessToken() = account.accessToken
    fun getRefreshToken() = account.refreshToken

    fun isFreePlan(): Boolean {
        val subscription = getSubscription()
        return isLocally() || subscription.plan == SubscriptionPlan.Free
    }

    fun getSubscription(): Subscription {

        if (isLocally().not()) {
            val enterprises = account.subscriptions.filter { it.plan == SubscriptionPlan.Enterprise }
            val teams = account.subscriptions.filter { it.plan == SubscriptionPlan.Team }
            val pros = account.subscriptions.filter { it.plan == SubscriptionPlan.Pro }
            val now = System.currentTimeMillis()

            if (enterprises.any { it.endDate > now }) {
                return enterprises.first { it.endDate > now }
            } else if (teams.any { it.endDate > now }) {
                return teams.first { it.endDate > now }
            } else if (pros.any { it.endDate > now }) {
                return pros.first { it.endDate > now }
            }
        }

        return Subscription(
            id = "0",
            plan = SubscriptionPlan.Free,
            startDate = 0,
            endDate = Long.MAX_VALUE
        )
    }

    /**
     * 刷新 Token
     */
    internal fun refreshToken() {
        val body = ohMyJson.encodeToString(mapOf("refreshToken" to getRefreshToken()))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("${server}/v1/token")
            .header("Authorization", "Bearer ${getRefreshToken()}")
            .post(body)
            .build()
        val response = Application.httpClient.newCall(request).execute()
        if (response.code == 401) {
            logout()
            throw ResponseException(response.code, response)
        }

        val text = response.use { response.body.use { it?.string() } }
        if (text == null) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        val json = ohMyJson.decodeFromString<JsonObject>(text)
        val accessToken = json["accessToken"]?.jsonPrimitive?.content
        val refreshToken = json["refreshToken"]?.jsonPrimitive?.content
        if (accessToken == null || refreshToken == null) {
            throw ResponseException(response.code, "token is empty", response)
        }

        // 登录成功
        account = Account(
            id = account.id,
            server = account.server,
            email = account.email,
            teams = account.teams,
            subscriptions = account.subscriptions,
            lastSynchronizationOn = account.lastSynchronizationOn,
            accessToken = accessToken,
            refreshToken = refreshToken,
            secretKey = account.secretKey,
            publicKey = account.publicKey,
            privateKey = account.privateKey
        )

        val accountProperties = AccountProperties.getInstance()
        accountProperties.accessToken = accessToken
        accountProperties.refreshToken = refreshToken


    }

    /**
     * 设置账户信息
     */
    internal fun login(account: Account) {
        assertEventDispatchThread()

        this.account = account

        // 立即保存到数据库
        val accountProperties = AccountProperties.getInstance()
        accountProperties.id = account.id
        accountProperties.server = account.server
        accountProperties.email = account.email
        accountProperties.teams = ohMyJson.encodeToString(account.teams)
        accountProperties.subscriptions = ohMyJson.encodeToString(account.subscriptions)
        accountProperties.lastSynchronizationOn = account.lastSynchronizationOn
        accountProperties.accessToken = account.accessToken
        accountProperties.refreshToken = account.refreshToken
        accountProperties.secretKey = ohMyJson.encodeToString(account.secretKey)

        if (isLocally().not()) {
            accountProperties.publicKey = Base64.encodeBase64String(account.publicKey.encoded)
            accountProperties.privateKey = Base64.encodeBase64String(account.privateKey.encoded)
        } else {
            accountProperties.publicKey = StringUtils.EMPTY
            accountProperties.privateKey = StringUtils.EMPTY
        }

        for (extension in ExtensionManager.getInstance().getExtensions(AccountExtension::class.java)) {
            extension.onAccountChanged()
        }
    }

    internal fun logout() {
        if (account.isLocally) return

        // 登入本地用户
        SwingUtilities.invokeLater { login(locally()) }

    }

    private fun locally(): Account {
        return Account(
            id = "0",
            server = "locally",
            email = "locally",
            teams = emptyList(),
            subscriptions = listOf(),
            lastSynchronizationOn = 0,
            secretKey = byteArrayOf(),
            accessToken = StringUtils.EMPTY,
            refreshToken = StringUtils.EMPTY,
            publicKey = object : PublicKey {
                override fun getAlgorithm(): String? {
                    TODO("Not yet implemented")
                }

                override fun getFormat(): String? {
                    TODO("Not yet implemented")
                }

                override fun getEncoded(): ByteArray? {
                    TODO("Not yet implemented")
                }

            },
            privateKey = object : PrivateKey {
                override fun getAlgorithm(): String? {
                    TODO("Not yet implemented")
                }

                override fun getFormat(): String? {
                    TODO("Not yet implemented")
                }

                override fun getEncoded(): ByteArray? {
                    TODO("Not yet implemented")
                }

            }
        )

    }

    override fun ready() {
        // 刷新 Token
        swingCoroutineScope.launch(Dispatchers.IO) { refreshToken() }
    }

    class AccountApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
        companion object {
            val instance by lazy { AccountApplicationRunnerExtension() }
        }

        override fun ready() {
            val accountManager = getInstance()
            val accountProperties = AccountProperties.getInstance()

            // 如果都是本地用户，那么可以忽略
            val id = accountProperties.id
            if (id.isBlank() || id == accountManager.getAccountId()) return

            // 初始化本地账户
            accountManager.account = Account(
                id = accountProperties.id,
                server = accountProperties.server,
                email = accountProperties.email,
                accessToken = accountProperties.accessToken,
                refreshToken = accountProperties.refreshToken,
                teams = ohMyJson.decodeFromString(accountProperties.teams),
                subscriptions = ohMyJson.decodeFromString(accountProperties.subscriptions),
                lastSynchronizationOn = accountProperties.lastSynchronizationOn,
                secretKey = ohMyJson.decodeFromString(accountProperties.secretKey),
                publicKey = RSA.generatePublic(Base64.decodeBase64(accountProperties.publicKey)),
                privateKey = RSA.generatePrivate(Base64.decodeBase64(accountProperties.privateKey))
            )

        }
    }


}