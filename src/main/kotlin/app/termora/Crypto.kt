package app.termora

import com.fasterxml.uuid.Generators
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.measureTime

private val jug = Generators.timeBasedEpochRandomGenerator(SecureRandom.getInstanceStrong())


fun randomUUID(): String {
    return jug.generate().toString().replace("-", StringUtils.EMPTY)
}

object AES {
    private const val ALGORITHM = "AES"

    /**
     * ECB 没有 IV
     */
    object ECB {
        private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"

        fun encrypt(key: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM))
            return cipher.doFinal(data)
        }

        fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM))
            return cipher.doFinal(data)
        }

    }

    /**
     * 携带 IV
     */
    object CBC {
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

        fun encrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv))
            return cipher.doFinal(data)
        }

        fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv))
            return cipher.doFinal(data)
        }


        fun String.aesCBCEncrypt(key: ByteArray, iv: ByteArray): ByteArray {
            return encrypt(key, iv, toByteArray())
        }

        fun ByteArray.aesCBCEncrypt(key: ByteArray, iv: ByteArray): ByteArray {
            return encrypt(key, iv, this)
        }

        fun ByteArray.aesCBCDecrypt(key: ByteArray, iv: ByteArray): ByteArray {
            return decrypt(key, iv, this)
        }

    }

    fun randomBytes(size: Int = 32): ByteArray {
        return RandomUtils.secureStrong().randomBytes(size)
    }

    fun ByteArray.encodeBase64String(): String {
        return Base64.encodeBase64String(this)
    }

    fun String.decodeBase64(): ByteArray {
        return Base64.decodeBase64(this)
    }
}


object PBKDF2 {

    private const val ALGORITHM = "PBKDF2WithHmacSHA512"
    private val log = LoggerFactory.getLogger(PBKDF2::class.java)

    fun generateSecret(
        password: CharArray,
        salt: ByteArray,
        iterationCount: Int = 150000,
        keyLength: Int = 256
    ): ByteArray {
        val bytes: ByteArray
        val time = measureTime {
            bytes = SecretKeyFactory.getInstance(ALGORITHM)
                .generateSecret(PBEKeySpec(password, salt, iterationCount, keyLength))
                .encoded
        }
        if (log.isDebugEnabled) {
            log.debug("Secret generated $time")
        }
        return bytes
    }

}


object RSA {

    private const val TRANSFORMATION = "RSA"

    fun encrypt(publicKey: PublicKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    fun decrypt(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(data)
    }

    fun encrypt(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(data)
    }

    fun decrypt(publicKey: PublicKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    fun generatePublic(publicKey: ByteArray): PublicKey {
        return KeyFactory.getInstance(TRANSFORMATION)
            .generatePublic(X509EncodedKeySpec(publicKey))
    }

    fun generatePrivate(privateKey: ByteArray): PrivateKey {
        return KeyFactory.getInstance(TRANSFORMATION)
            .generatePrivate(PKCS8EncodedKeySpec(privateKey))
    }

    fun generateKeyPair(keySize: Int = 2048): KeyPair {
        val generator = KeyPairGenerator.getInstance(TRANSFORMATION)
        generator.initialize(keySize)
        return generator.generateKeyPair()
    }
}