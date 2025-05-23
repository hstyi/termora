package app.termora.db

import app.termora.randomUUID
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.RandomUtils

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
)
