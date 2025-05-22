package app.termora.db

import app.termora.LocalSecret
import app.termora.randomUUID
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.crypt.Algorithms

object Data : Table() {
    val id: Column<String> = char("id", length = 32).clientDefault { randomUUID() }

    /**
     * 归属者
     */
    val ownerId: Column<String> = char("ownerId", length = 32)

    /**
     * [OwnerType]
     */
    val ownerType: Column<String> = varchar("ownerType", 32)

    /**
     * [DataType]
     */
    val type: Column<String> = varchar("type", 32)

    /**
     * 版本，当和数据库不一致时会同步，以最大的为准
     */
    val version = integer("version").clientDefault { 0 }

    /**
     * 是否已经同步标识，每次更新都要设置成 false 否则不会同步
     */
    val synced: Column<Boolean> = bool("synced").clientDefault { false }

    /**
     * 数据
     */
    val data: Column<String> = encryptedText(
        "data", Algorithms.AES_256_PBE_GCM(
            LocalSecret.getInstance().password,
            LocalSecret.getInstance().salt
        )
    )

    override val primaryKey: PrimaryKey get() = PrimaryKey(id)
}
