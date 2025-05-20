package app.termora.db

import app.termora.Application
import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.Disposable
import app.termora.swingCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DatabaseManager private constructor() : Disposable {
    companion object {
        val log = LoggerFactory.getLogger(DatabaseManager::class.java)!!
        fun getInstance(): DatabaseManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(DatabaseManager::class) { DatabaseManager() }
        }
    }

    val database: Database
    val lock = ReentrantLock()
    private val map = mutableMapOf<String, String>()

//    val sftp by lazy { SFTP() }


    init {

        val databaseFile = File(Application.getDatabaseFile(), "termora.db")
        val isExists = databaseFile.exists()

        database = Database.connect(
            "jdbc:sqlite:${databaseFile.absolutePath}",
            driver = "org.sqlite.JDBC", user = "sa"
        )

        // 设置数据库版本号，便于后续升级
        if (isExists.not()) {
            transaction(database) {
                // 创建数据库
                SchemaUtils.create(Data, Settings)
                @Suppress("SqlNoDataSourceInspection")
                exec("PRAGMA db_version = 1", explicitStatementType = StatementType.UPDATE)
            }
        }

        // 异步初始化
        swingCoroutineScope.launch(Dispatchers.IO) {
            map.putAll(getSettings())
        }

    }

    /**
     * 返回本地所有用户的数据，调用者需要过滤具体用户
     */
    inline fun <reified T> data(type: DataType): List<T> {
        val list = mutableListOf<T>()
        try {
            for (text in rawData(type)) {
                list.add(ohMyJson.decodeFromString<T>(text))
            }
        } catch (e: Exception) {
            if (log.isWarnEnabled) {
                log.warn(e.message, e)
            }
        }
        return list
    }


    fun rawData(type: DataType): List<String> {
        val list = mutableListOf<String>()
        lock.withLock {
            transaction(database) {
                val rows = Data.selectAll().where { (Data.type eq type.name) }.toList()
                for (row in rows) {
                    try {
                        list.add(row[Data.data])
                    } catch (e: Exception) {
                        if (log.isWarnEnabled) {
                            log.warn(e.message, e)
                        }
                    }
                }
            }
        }
        return list
    }

    fun save(ownerId: String, ownerType: OwnerType, id: String, type: DataType, data: String) {
        lock.withLock {
            transaction(database) {
                val exists = Data.selectAll()
                    .where { (Data.id eq id) and (Data.type eq type.name) and (Data.ownerType eq ownerType.name) }
                    .any()

                if (exists) {
                    Data.update({ (Data.id eq id) }) {
                        it[Data.data] = data
                    }
                } else {
                    Data.insert {
                        it[Data.id] = id
                        it[Data.ownerId] = ownerId
                        it[Data.ownerType] = ownerType.name
                        it[Data.type] = type.name
                        it[Data.data] = data
                    }
                }
            }
        }
    }

    fun delete(id: String) {
        lock.withLock {
            transaction(database) {
                Data.deleteWhere { Data.id eq id }
            }
        }
    }

    private fun getSettings(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        lock.withLock {
            transaction(database) {
                for (row in Settings.selectAll().toList()) {
                    map[row[Settings.name]] = row[Settings.value]
                }
            }
        }
        return map
    }

}