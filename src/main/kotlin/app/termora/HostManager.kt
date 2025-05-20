package app.termora

import app.termora.Application.ohMyJson
import app.termora.account.AccountManager
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.OwnerType


class HostManager private constructor() {
    companion object {
        fun getInstance(): HostManager {
            return ApplicationScope.forApplicationScope().getOrCreate(HostManager::class) { HostManager() }
        }
    }

    private val database get() = DatabaseManager.getInstance()
    private val accountManager get() = AccountManager.getInstance()
    private var hosts = mutableMapOf<String, Host>()

    /**
     * 修改缓存并存入数据库
     */
    fun addHost(host: Host) {
        assertEventDispatchThread()
        if (host.deleted) {
            removeHost(host.id)
        } else {
            val ownerType = runCatching { OwnerType.valueOf(host.ownerType) }.getOrNull() ?: OwnerType.User
            database.save(
                host.ownerId, ownerType, host.id,
                DataType.Host, ohMyJson.encodeToString(host)
            )
            hosts[host.id] = host
        }
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
        return hosts.values.filter { !it.deleted }
            .sortedWith(compareBy<Host> { if (it.isFolder) 0 else 1 }.thenBy { it.sort })
    }

    /**
     * 从缓存中获取
     */
    fun getHost(id: String): Host? {
        return hosts[id]
    }

}