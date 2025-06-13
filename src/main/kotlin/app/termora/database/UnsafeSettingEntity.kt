package app.termora.database

import app.termora.randomUUID
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

internal object UnsafeSettingEntity : Table() {
    val id: Column<String> = char("id", length = 32).clientDefault { randomUUID() }
    val name: Column<String> = varchar("name", length = 128).index()
    val value: Column<String> = text("value")

    /**
     * 备用字段1-3
     */
    val extra1: Column<String> = text("extra1").clientDefault { StringUtils.EMPTY }
    val extra2: Column<String> = text("extra2").clientDefault { StringUtils.EMPTY }
    val extra3: Column<String> = text("extra3").clientDefault { StringUtils.EMPTY }

    override val primaryKey: PrimaryKey get() = PrimaryKey(id)
    override val tableName: String
        get() = "tb_unsafe_setting"
}
