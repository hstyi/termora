package app.termora.sftp

import java.util.*

interface TransportListener : EventListener {
    /**
     * 状态变化
     */
    fun onTransportChanged(transport: Transport) {}
}