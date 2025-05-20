package app.termora.db

import app.termora.toSimpleString
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.crypt.Algorithms
import java.util.*

object Settings : Table() {
    val id: Column<String> = char("id", length = 32).clientDefault { UUID.randomUUID().toSimpleString() }
    val name: Column<String> = varchar("name", length = 128).uniqueIndex()
    val value: Column<String> = encryptedText("data", Algorithms.AES_256_PBE_GCM("password", "1234561234561234"))
    override val primaryKey: PrimaryKey get() = PrimaryKey(id)
}
