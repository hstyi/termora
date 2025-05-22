package app.termora.account

import org.apache.commons.lang3.StringUtils
import java.security.PrivateKey
import java.security.PublicKey

class Account(
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
     * 最后同步时间
     */
    val lastSynchronizationOn: Long,

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
        get() = id == "0" || StringUtils.equalsIgnoreCase(email, "locally") ||
                StringUtils.equalsIgnoreCase(server, "locally")
}