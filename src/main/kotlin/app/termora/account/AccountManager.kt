package app.termora.account

import app.termora.Application.ohMyJson
import app.termora.ApplicationRunnerExtension
import app.termora.ApplicationScope
import app.termora.RSA
import org.apache.commons.codec.binary.Base64
import java.security.PrivateKey
import java.security.PublicKey

class AccountManager private constructor() : ApplicationRunnerExtension {
    companion object {
        fun getInstance(): AccountManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(AccountManager::class) { AccountManager() }
        }
    }

    private var account = Account(
        id = "0",
        server = "locally",
        email = "locally",
        teams = emptyList(),
        subscriptions = listOf(),
        lastSynchronizationOn = 0,
        secretKey = byteArrayOf(),
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

    fun getSubscription(): Subscription {

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

        return Subscription(
            id = "0",
            plan = SubscriptionPlan.Free,
            startDate = 0,
            endDate = Long.MAX_VALUE
        )
    }

    override fun ready() {

    }

    class AccountApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
        companion object {
            val instance by lazy { AccountApplicationRunnerExtension() }
        }

        override fun ready() {
            val accountManager = getInstance()
            val accountProperties = AccountProperties.getInstance()

            // 如果都是本地用户，那么可以忽略
            if (accountProperties.id == accountManager.getAccountId()) return

            // 初始化本地账户
            accountManager.account = Account(
                id = accountProperties.id,
                server = accountProperties.server,
                email = accountProperties.email,
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