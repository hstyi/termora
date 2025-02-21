package app.termora

import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis


class HostManager private constructor() {
    companion object {
        fun getInstance(): HostManager {
            return ApplicationScope.forApplicationScope().getOrCreate(HostManager::class) { HostManager() }
        }

        private val log = LoggerFactory.getLogger(HostManager::class.java)
    }

    private val database get() = Database.getDatabase()

    fun addHost(host: Host) {
        assertEventDispatchThread()
        database.addHost(host)
    }

    fun hosts(): List<Host> {
        val hosts: List<Host>
        measureTimeMillis {
            hosts = database.getHosts()
                .filter { !it.deleted }
                .sortedWith(compareBy<Host> { if (it.protocol == Protocol.Folder) 0 else 1 }.thenBy { it.sort })
        }.let {
            if (log.isDebugEnabled) {
                log.debug("hosts: $it ms")
            }
        }
        return hosts
    }

}