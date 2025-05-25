package app.termora.account

import org.apache.commons.lang3.StringUtils
import java.security.PrivateKey
import java.security.PublicKey

data class Account(
    /**
     * 账户唯一 ID
     */
    val id: String,

    /**
     * 后台服务
     */
    val server: String,

    /**
     * 账号
     */
    val email: String,

    /**
     * 加入的团队
     */
    val teams: List<Team>,

    /**
     * 订阅
     */
    val subscriptions: List<Subscription>,


    /**
     * 访问 Token
     */
    val accessToken: String,

    /**
     * 刷新 Token
     */
    val refreshToken: String,

    /**
     * 用户的密钥
     */
    val secretKey: ByteArray,

    /**
     * 用户公钥
     */
    val publicKey: PublicKey,

    /**
     * 用户私钥
     */
    val privateKey: PrivateKey,
) {

    val isLocally
        get() = (id == "0" || StringUtils.equalsIgnoreCase(email, "locally") ||
                StringUtils.equalsIgnoreCase(server, "locally"))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Account

        if (id != other.id) return false
        if (server != other.server) return false
        if (email != other.email) return false
        if (teams != other.teams) return false
        if (subscriptions != other.subscriptions) return false
        if (accessToken != other.accessToken) return false
        if (refreshToken != other.refreshToken) return false
        if (!secretKey.contentEquals(other.secretKey)) return false
        if (publicKey != other.publicKey) return false
        if (privateKey != other.privateKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + server.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + teams.hashCode()
        result = 31 * result + subscriptions.hashCode()
        result = 31 * result + accessToken.hashCode()
        result = 31 * result + refreshToken.hashCode()
        result = 31 * result + secretKey.contentHashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + privateKey.hashCode()
        return result
    }
}