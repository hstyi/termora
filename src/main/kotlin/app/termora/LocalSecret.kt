package app.termora

import app.termora.native.KeyStorageProvider
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.RandomStringUtils

internal class LocalSecret private constructor() {

    companion object {
        fun getInstance(): LocalSecret {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(LocalSecret::class) { LocalSecret() }
        }
    }

    /**
     * 一个 16 长度的密码
     */
    val password: String

    /**
     * 一个 16 长度的盐
     */
    val salt: ByteArray

    init {
        val keyStorage = KeyStorageProvider.getInstance().getKeyStorage()
        val name = if (Application.isUnknownVersion()) "local-secret-dev2" else "local-secret"
        var password = keyStorage.getPassword(Application.getName(), name)
        if (password == null) {
            // 随机生成密码
            password = RandomStringUtils.secureStrong().nextAlphanumeric(16)
            if (keyStorage.setPassword(Application.getName(), name, password).not()) {
                throw IllegalArgumentException("Unable to access secret repository")
            }
        }
        this.password = password

        // 生成随机盐
        this.salt = DigestUtils.sha256(password).copyOf(16)
    }
}