package app.termora.db

import app.termora.LocalSecret
import app.termora.randomUUID
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.crypt.Algorithms

object Settings : Table() {
    val id: Column<String> = char("id", length = 32).clientDefault { randomUUID() }
    val ownerId: Column<String> = char("ownerId", length = 32)
    val ownerType: Column<String> = varchar("ownerType", 32)
    val name: Column<String> = varchar("name", length = 128).uniqueIndex()
    val value: Column<String> = encryptedText(
        "value", Algorithms.AES_256_PBE_GCM(
            LocalSecret.getInstance().password,
            LocalSecret.getInstance().salt
        )
    )
    override val primaryKey: PrimaryKey get() = PrimaryKey(id)
}
