package app.termora.db

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.plugin.ExtensionManager
import app.termora.terminal.CursorStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DatabaseManager private constructor() : Disposable {
    companion object {
        val log = LoggerFactory.getLogger(DatabaseManager::class.java)!!
        fun getInstance(): DatabaseManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(DatabaseManager::class) { DatabaseManager() }
        }
    }

    val database: Database
    val lock = ReentrantLock()

    val properties by lazy { Properties(this) }
    val terminal by lazy { Terminal(this) }
    val appearance by lazy { Appearance(this) }
    val sftp by lazy { SFTP(this) }

    private val map = mutableMapOf<String, String>()


    init {

        val databaseFile = FileUtils.getFile(
            Application.getBaseDataDir(),
            "config", "termora.db"
        )
        FileUtils.forceMkdirParent(databaseFile)
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

        for (extension in ExtensionManager.getInstance().getExtensions(DatabaseManagerExtension::class.java)) {
            extension.ready(this)
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

        for (extension in ExtensionManager.getInstance().getExtensions(DatabaseManagerExtension::class.java)) {
            try {
                extension.onDataChanged(id, type, data)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
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

    fun getSettings(): Map<String, String> {
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

    fun setSetting(name: String, value: String) {
        val accountManager = AccountManager.getInstance()
        lock.withLock {
            transaction(database) {
                for (row in Settings.selectAll().where { Settings.name eq name }.toList()) {
                    Settings.deleteWhere { Settings.id eq row[Settings.id] }
                }
                Settings.insert {
                    it[Settings.name] = name
                    it[Settings.value] = value
                    it[Settings.ownerId] = accountManager.getAccountId()
                    it[Settings.ownerType] = OwnerType.User.name
                }
            }
            map[name] = value
        }
    }

    abstract class IProperties(
        private val databaseManager: DatabaseManager,
        private val name: String
    ) {

        protected open fun getString(key: String): String? {
            val c = "${name}.$key"
            return databaseManager.map[c]
        }


        protected open fun putString(key: String, value: String) {
            val c = "${name}.$key"
            databaseManager.setSetting(c, value)
        }


        fun getProperties(): Map<String, String> {
            val properties = mutableMapOf<String, String>()
            for (e in databaseManager.map.entries) {
                if (e.key.startsWith("${name}.")) {
                    properties[e.key] = e.value
                }
            }
            return properties
        }


        protected abstract inner class PropertyLazyDelegate<T>(protected val initializer: () -> T) :
            ReadWriteProperty<Any?, T> {
            private var value: T? = null

            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                if (value == null) {
                    val v = getString(property.name)
                    value = if (v == null) {
                        initializer.invoke()
                    } else {
                        convertValue(v)
                    }
                }

                if (value == null) {
                    value = initializer.invoke()
                }
                return value!!
            }

            abstract fun convertValue(value: String): T

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                this.value = value
                putString(property.name, value.toString())
            }

        }

        protected abstract inner class PropertyDelegate<T>(private val defaultValue: T) :
            PropertyLazyDelegate<T>({ defaultValue })


        protected inner class StringPropertyDelegate(defaultValue: String) :
            PropertyDelegate<String>(defaultValue) {
            override fun convertValue(value: String): String {
                return value
            }
        }

        protected inner class IntPropertyDelegate(defaultValue: Int) :
            PropertyDelegate<Int>(defaultValue) {
            override fun convertValue(value: String): Int {
                return value.toIntOrNull() ?: initializer.invoke()
            }
        }

        protected inner class DoublePropertyDelegate(defaultValue: Double) :
            PropertyDelegate<Double>(defaultValue) {
            override fun convertValue(value: String): Double {
                return value.toDoubleOrNull() ?: initializer.invoke()
            }
        }


        protected inner class LongPropertyDelegate(defaultValue: Long) :
            PropertyDelegate<Long>(defaultValue) {
            override fun convertValue(value: String): Long {
                return value.toLongOrNull() ?: initializer.invoke()
            }
        }

        protected inner class BooleanPropertyDelegate(defaultValue: Boolean) :
            PropertyDelegate<Boolean>(defaultValue) {
            override fun convertValue(value: String): Boolean {
                return value.toBooleanStrictOrNull() ?: initializer.invoke()
            }
        }

        protected open inner class StringPropertyLazyDelegate(initializer: () -> String) :
            PropertyLazyDelegate<String>(initializer) {
            override fun convertValue(value: String): String {
                return value
            }
        }


        protected inner class CursorStylePropertyDelegate(defaultValue: CursorStyle) :
            PropertyDelegate<CursorStyle>(defaultValue) {
            override fun convertValue(value: String): CursorStyle {
                return try {
                    CursorStyle.valueOf(value)
                } catch (_: Exception) {
                    initializer.invoke()
                }
            }
        }


    }

    /**
     * 终端设置
     */
    class Terminal(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.Terminal") {

        /**
         * 字体
         */
        var font by StringPropertyDelegate("JetBrains Mono")

        /**
         * 默认终端
         */
        var localShell by StringPropertyLazyDelegate { Application.getDefaultShell() }

        /**
         * 字体大小
         */
        var fontSize by IntPropertyDelegate(14)

        /**
         * 最大行数
         */
        var maxRows by IntPropertyDelegate(5000)

        /**
         * 调试模式
         */
        var debug by BooleanPropertyDelegate(false)

        /**
         * 蜂鸣声
         */
        var beep by BooleanPropertyDelegate(true)

        /**
         * 超链接
         */
        var hyperlink by BooleanPropertyDelegate(true)

        /**
         * 光标闪烁
         */
        var cursorBlink by BooleanPropertyDelegate(false)

        /**
         * 选中复制
         */
        var selectCopy by BooleanPropertyDelegate(false)

        /**
         * 光标样式
         */
        var cursor by CursorStylePropertyDelegate(CursorStyle.Block)

        /**
         * 终端断开连接时自动关闭Tab
         */
        var autoCloseTabWhenDisconnected by BooleanPropertyDelegate(false)

        /**
         * 是否显示悬浮工具栏
         */
        var floatingToolbar by BooleanPropertyDelegate(true)
    }

    /**
     * 通用属性
     */
    class Properties(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.Properties") {
        public override fun getString(key: String): String? {
            return super.getString(key)
        }


        fun getString(key: String, defaultValue: String): String {
            return getString(key) ?: defaultValue
        }

        public override fun putString(key: String, value: String) {
            super.putString(key, value)
        }
    }

    /**
     * 外观
     */
    class Appearance(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.Appearance") {


        /**
         * 外观
         */
        var theme by StringPropertyDelegate("Light")

        /**
         * 跟随系统
         */
        var followSystem by BooleanPropertyDelegate(true)
        var darkTheme by StringPropertyDelegate("Dark")
        var lightTheme by StringPropertyDelegate("Light")

        /**
         * 允许后台运行，也就是托盘
         */
        var backgroundRunning by BooleanPropertyDelegate(false)

        /**
         * 背景图片的地址
         */
        var backgroundImage by StringPropertyDelegate(StringUtils.EMPTY)

        /**
         * 语言
         */
        var language by StringPropertyLazyDelegate {
            I18n.containsLanguage(Locale.getDefault()) ?: Locale.US.toString()
        }


        /**
         * 透明度
         */
        var opacity by DoublePropertyDelegate(1.0)
    }

    /**
     * SFTP
     */
    class SFTP(databaseManager: DatabaseManager) : IProperties(databaseManager, "Setting.SFTP") {


        /**
         * 编辑命令
         */
        var editCommand by StringPropertyDelegate(StringUtils.EMPTY)


        /**
         * sftp command
         */
        var sftpCommand by StringPropertyDelegate(StringUtils.EMPTY)

        /**
         * defaultDirectory
         */
        var defaultDirectory by StringPropertyDelegate(StringUtils.EMPTY)


        /**
         * 是否固定在标签栏
         */
        var pinTab by BooleanPropertyDelegate(false)

        /**
         * 是否保留原始文件时间
         */
        var preserveModificationTime by BooleanPropertyDelegate(false)

    }

}