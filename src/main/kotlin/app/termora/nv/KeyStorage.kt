package app.termora.nv

import org.slf4j.LoggerFactory

interface KeyStorage {

    companion object {
        private val log = LoggerFactory.getLogger(KeyStorage::class.java)
    }

    fun setPassword(serviceName: String, username: String, password: String): Boolean
    fun getPassword(serviceName: String, username: String): String?
    fun deletePassword(serviceName: String, username: String): Boolean
}