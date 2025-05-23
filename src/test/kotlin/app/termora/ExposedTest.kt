package app.termora

import app.termora.db.DataEntity
import app.termora.db.DataType
import app.termora.db.OwnerType
import app.termora.db.SettingEntity
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test


class ExposedTest {


    @Test
    fun test() {
        val database = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "sa")

        transaction(database) {
            SchemaUtils.create(DataEntity, SettingEntity)

            println(DataEntity.insert {
                it[ownerId] = "Test"
                it[ownerType] = OwnerType.User.name
                it[type] = DataType.KeywordHighlight.name
                it[data] = "hello 中文".repeat(10000)
            } get DataEntity.id)

            println(SettingEntity.insert {
                it[name] = "Test"
                it[value] = "hello 中文".repeat(10000)
            } get SettingEntity.id)


        }
    }
}