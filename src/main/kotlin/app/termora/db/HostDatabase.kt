package app.termora.db

import app.termora.Application.ohMyJson
import app.termora.Host
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock


class HostDatabase(database: Database, lock: Lock) : MyDatabase(database, lock) {
    companion object {
        private val log = LoggerFactory.getLogger(HostDatabase::class.java)
    }

    fun hosts(): List<Host> {
        val hosts = mutableListOf<Host>()
        lock.withLock {
            transaction(database) {
                val list = Data.selectAll().where { Data.type eq DataType.Host.name }.toList()
                for (row in list) {
                    try {
                        hosts.add(ohMyJson.decodeFromString<Host>(row[Data.data]))
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            }
        }
        return hosts
    }

    fun save(host: Host) {
        lock.withLock {
            transaction(database) {
                // 如果包存在，那么修改
                if (Data.selectAll().where { (Data.id eq host.id) and (Data.type eq DataType.Host.name) }.any()) {
                    Data.update({ (Data.id eq host.id) and (Data.type eq DataType.Host.name) }) {
                        it[data] = ohMyJson.encodeToString(host)
                    }
                } else {
                    Data.insert {
                        it[ownerId] = host.ownerId
                        it[id] = host.id
                        it[ownerType] = host.ownerType
                        it[type] = DataType.Host.name
                        it[data] = ohMyJson.encodeToString(host)
                    }
                }
            }
        }
    }

    fun delete(id: String) {

    }
}