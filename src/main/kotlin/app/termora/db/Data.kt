package app.termora.db

import app.termora.LocalSecret
import app.termora.randomUUID
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.crypt.Algorithms

object Data : Table() {
    val id: Column<String> = char("id", length = 32).clientDefault { randomUUID() }
    val ownerId: Column<String> = char("ownerId", length = 32)
    val ownerType: Column<String> = varchar("ownerType", 32)
    val type: Column<String> = varchar("type", 32)
    val data: Column<String> = encryptedText(
        "data", Algorithms.AES_256_PBE_GCM(
            LocalSecret.getInstance().password,
            Hex.encodeHexString(LocalSecret.getInstance().salt)
        )
    )
    override val primaryKey: PrimaryKey get() = PrimaryKey(id)
}
