package app.termora

import app.termora.Application.ohMyJson
import app.termora.db.Data
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.DatabaseManagerExtension
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import org.apache.commons.lang3.StringUtils


class HostManager private constructor() : Disposable {
    companion object {
        fun getInstance(): HostManager {
            return ApplicationScope.forApplicationScope().getOrCreate(HostManager::class) { HostManager() }
        }
    }

    init {
        Disposer.register(
            this, DynamicExtensionHandler.getInstance()
            .register(DatabaseManagerExtension::class.java, object : DatabaseManagerExtension {
                override fun onDataChanged(id: String, type: String) {
                    if (StringUtils.isBlank(type)) {
                        hosts.remove(id)
                    }
                }
            })
        )
    }

    private val databaseManager get() = DatabaseManager.getInstance()
    private var hosts = mutableMapOf<String, Host>()

    /**
     * 修改缓存并存入数据库
     */
    fun addHost(host: Host) {
        assertEventDispatchThread()

        databaseManager.saveAndIncrementVersion(
            Data(
                id = host.id,
                ownerId = host.ownerId,
                ownerType = host.ownerType,
                type = DataType.Host.name,
                data = ohMyJson.encodeToString(host),
            )
        )

        hosts[host.id] = host
    }

    fun removeHost(id: String) {
        hosts.entries.removeIf { it.value.id == id || it.value.parentId == id }
        databaseManager.delete(id)
        DeleteDataManager.getInstance().removeHost(id)
    }

    /**
     * 第一次调用从数据库中获取，后续从缓存中获取
     */
    fun hosts(): List<Host> {
        if (hosts.isEmpty()) {
            databaseManager.data<Host>(DataType.Host).forEach { hosts[it.id] = it }
        }
        return hosts.values
            .sortedWith(compareBy<Host> { if (it.isFolder) 0 else 1 }.thenBy { it.sort })
    }

    /**
     * 从缓存中获取
     */
    fun getHost(id: String): Host? {
        return hosts[id]
    }

}