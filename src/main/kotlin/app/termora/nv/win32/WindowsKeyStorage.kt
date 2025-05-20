package app.termora.nv.win32

import app.termora.ApplicationScope
import app.termora.nv.KeyStorage
import app.termora.nv.win32.CredAdvapi32.*
import com.sun.jna.LastErrorException
import com.sun.jna.Memory
import com.sun.jna.Pointer
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*


class WindowsKeyStorage private constructor() : KeyStorage {

    companion object {
        private val log = LoggerFactory.getLogger(WindowsKeyStorage::class.java)

        fun getInstance(): WindowsKeyStorage {
            return ApplicationScope.forApplicationScope().getOrCreate(WindowsKeyStorage::class) { WindowsKeyStorage() }
        }

        private fun getPointer(array: ByteArray): Pointer {
            val p: Pointer = Memory(array.size.toLong())
            p.write(0, array, 0, array.size)
            return p
        }


        private fun buildCred(key: String, username: String, credentialBlob: ByteArray): CREDENTIAL {
            val credential = CREDENTIAL()

            credential.Flags = 0
            credential.Type = CredAdvapi32.CRED_TYPE_GENERIC
            credential.TargetName = key


            credential.CredentialBlobSize = credentialBlob.size
            credential.CredentialBlob = getPointer(credentialBlob)

            credential.Persist = CredAdvapi32.CRED_PERSIST_LOCAL_MACHINE
            credential.UserName = username

            return credential
        }


        private fun UTF16LEGetBytes(value: CharArray?): ByteArray {
            return Charsets.UTF_16LE.encode(CharBuffer.wrap(value)).array()
        }


        private fun UTF16LEGetString(bytes: ByteArray?): CharArray {
            return Charsets.UTF_16LE.decode(ByteBuffer.wrap(bytes)).array()
        }

    }

    override fun setPassword(
        serviceName: String,
        username: String,
        password: String
    ): Boolean {
        return writeSecret(serviceName, username, password.toCharArray())
    }

    override fun getPassword(serviceName: String, username: String): String? {
        val pcredential = PCREDENTIAL()
        val read: Boolean

        try {
            // MSDN doc doesn't mention threading safety, so let's just be careful and synchronize the access
            synchronized(INSTANCE) {
                read = INSTANCE.CredRead(serviceName, CredAdvapi32.CRED_TYPE_GENERIC, 0, pcredential)
            }

            if (read) {
                return createSecret(CREDENTIAL(pcredential.credential))
            }
        } catch (e: LastErrorException) {
            log.error("Getting secret failed. {}", e.message)
        } finally {
            if (pcredential.credential != null) {
                synchronized(INSTANCE) {
                    INSTANCE.CredFree(pcredential.credential)
                }
            }
        }
        return null
    }

    override fun deletePassword(serviceName: String, username: String): Boolean {
        return deleteSecret(serviceName)
    }

    private fun writeSecret(key: String, username: String, secret: CharArray): Boolean {
        val credBlob = UTF16LEGetBytes(secret)
        val cred = buildCred(key, username, credBlob)
        try {
            synchronized(INSTANCE) {
                INSTANCE.CredWrite(cred, 0)
            }
            return true
        } catch (e: LastErrorException) {
            log.error("Adding secret failed. {}", e.message)
            return false
        } finally {
            cred.CredentialBlob.clear(credBlob.size.toLong())
            Arrays.fill(credBlob, 0.toByte())
        }
    }

    private fun deleteSecret(key: String): Boolean {
        try {
            synchronized(INSTANCE) {
                return INSTANCE.CredDelete(key, CredAdvapi32.CRED_TYPE_GENERIC, 0)
            }
        } catch (e: LastErrorException) {
            log.error("Deleting secret failed. {}", e.message)
            return false
        }
    }

    private fun getSecret(credential: CREDENTIAL): CharArray {
        val secretData = credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize)
        return UTF16LEGetString(secretData)
    }

    private fun createSecret(credential: CREDENTIAL): String {
        return String(getSecret(credential))
    }


}