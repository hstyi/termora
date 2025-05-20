package app.termora.db

import app.termora.Application.ohMyJson
import app.termora.Host
import app.termora.toSimpleString
import java.util.*
import kotlin.test.Test

class DatabaseManagerTest {
    @Test
    fun test() {
        val databaseManager = DatabaseManager.getInstance()
        for (i in 0 until 5) {
            databaseManager.save(
                "1",
                OwnerType.User,
                UUID.randomUUID().toSimpleString(),
                DataType.Host,
                ohMyJson.encodeToString(
                    Host(
                        name = UUID.randomUUID().toSimpleString(),
                        protocol = UUID.randomUUID().toSimpleString(),
                        ownerType = OwnerType.User.name
                    )
                )
            )
        }
        println(databaseManager.data<Host>("1", OwnerType.User,DataType.Host))
    }
}