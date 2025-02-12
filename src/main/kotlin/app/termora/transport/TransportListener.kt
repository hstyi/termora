package app.termora.transport

import java.util.*

interface TransportListener : EventListener {

    companion object {
        val EMPTY = object : TransportListener {
            override fun onTransportAdded(transport: Transport) {

            }

            override fun onTransportRemoved(transport: Transport) {
            }

            override fun onTransportChanged(transport: Transport) {
            }
        }
    }

    /**
     * Added
     */
    fun onTransportAdded(transport: Transport){}

    /**
     * Removed
     */
    fun onTransportRemoved(transport: Transport){}

    /**
     * 状态变化
     */
    fun onTransportChanged(transport: Transport){}
}