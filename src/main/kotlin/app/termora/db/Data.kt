package app.termora.db

import app.termora.randomUUID
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.RandomUtils
import org.jetbrains.exposed.v1.core.ResultRow

data class Data(
    val id: String = randomUUID(),
    val ownerId: String,
    val ownerType: String,
    val type: String,
    val data: String,
    val version: Long = 0,
    val synced: Boolean = false,
    val deleted: Boolean = false,
    val iv: String = Base64.encodeBase64String(RandomUtils.secureStrong().randomBytes(12)),
) {
    companion object {
        fun ResultRow.toData(): Data {
            return Data(
                id = this[DataEntity.id],
                ownerId = this[DataEntity.ownerId],
                ownerType = this[DataEntity.ownerType],
                type = this[DataEntity.type],
                data = this[DataEntity.data],
                version = this[DataEntity.version],
                synced = this[DataEntity.synced],
                deleted = this[DataEntity.deleted],
                iv = this[DataEntity.iv],
            )
        }
    }
}
