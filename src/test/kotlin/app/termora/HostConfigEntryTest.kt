package app.termora

import org.apache.sshd.client.config.hosts.HostConfigEntry
import kotlin.test.Test

class HostConfigEntryTest {
    @Test
    fun test() {
        val entries = HostConfigEntry.readHostConfigEntries(HostConfigEntry.getDefaultHostConfigFile())
        for (entry in entries) {
            println(entry.host)
        }
    }
}