package app.termora.db

import app.termora.LocalSecret
import app.termora.toSimpleString
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.crypt.Algorithms
import java.util.*

object Settings : Table() {
    val id: Column<String> = char("id", length = 32).clientDefault { UUID.randomUUID().toSimpleString() }
    val name: Column<String> = varchar("name", length = 128).uniqueIndex()
    val value: Column<String> = encryptedText(
        "data", Algorithms.AES_256_PBE_GCM(
            LocalSecret.getInstance().password,
            Hex.encodeHexString(LocalSecret.getInstance().salt)
        )
    )
    override val primaryKey: PrimaryKey get() = PrimaryKey(id)
}
