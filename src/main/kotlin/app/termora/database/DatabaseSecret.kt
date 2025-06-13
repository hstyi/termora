package app.termora.database

import app.termora.ApplicationScope
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal class DatabaseSecret(database: Database) {

    companion object {
        private const val PASSWORD = "__DB_PASSWORD"
        private const val SALT = "__DB_SALT"

        fun getInstance(database: Database): DatabaseSecret {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(DatabaseSecret::class) { DatabaseSecret(database) }
        }

        fun getInstance(): DatabaseSecret {
            return ApplicationScope.forApplicationScope().get(DatabaseSecret::class)
        }
    }

    @Volatile
    var password: String = StringUtils.EMPTY
        private set

    @Volatile
    var salt: String = StringUtils.EMPTY
        private set

    init {
        transaction(database) {
            val unsafeSettings = UnsafeSettingEntity.selectAll()
                .map { it[UnsafeSettingEntity.name] to it[UnsafeSettingEntity.value] }
                .associateBy { it.first }
            if (unsafeSettings.containsKey(PASSWORD)) {
                password = unsafeSettings.getValue(PASSWORD).second
            } else {
                password =
                    StringUtils.substring(DigestUtils.sha256Hex(RandomUtils.secureStrong().randomBytes(128)), 0, 16)
                UnsafeSettingEntity.insert {
                    it[UnsafeSettingEntity.name] = PASSWORD
                    it[UnsafeSettingEntity.value] = password
                }
            }

            if (unsafeSettings.containsKey(SALT)) {
                salt = unsafeSettings.getValue(SALT).second
            } else {
                salt =
                    StringUtils.substring(DigestUtils.sha256Hex(RandomUtils.secureStrong().randomBytes(128)), 0, 12)
                UnsafeSettingEntity.insert {
                    it[UnsafeSettingEntity.name] = SALT
                    it[UnsafeSettingEntity.value] = salt
                }
            }
        }
    }

}