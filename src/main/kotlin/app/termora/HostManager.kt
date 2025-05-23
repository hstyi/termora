package app.termora

import app.termora.Application.ohMyJson
import app.termora.db.Data
import app.termora.db.DataType
import app.termora.db.DatabaseManager


class HostManager private constructor() {
    companion object {
        fun getInstance(): HostManager {
            return ApplicationScope.forApplicationScope().getOrCreate(HostManager::class) { HostManager() }
        }
    }

    private val database get() = DatabaseManager.getInstance()
    private var hosts = mutableMapOf<String, Host>()

    /**
     * 修改缓存并存入数据库
     */
    fun addHost(host: Host) {
        assertEventDispatchThread()
        database.save(
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
        database.delete(id)
        DeleteDataManager.getInstance().removeHost(id)
    }

    /**
     * 第一次调用从数据库中获取，后续从缓存中获取
     */
    fun hosts(): List<Host> {
        if (hosts.isEmpty()) {
            database.data<Host>(DataType.Host).forEach { hosts[it.id] = it }
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