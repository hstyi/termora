package app.termora.sftp

interface TransportManager {
    fun addTransport(transport: Transport): Boolean
    fun getTransport(id: Long): Transport?
    fun getTransports(pId: Long): List<Transport>
    fun getTransportCount(): Int
    fun removeTransport(id: Long)
    fun addTransportListener(listener: TransportListener)
    fun removeTransportListener(listener: TransportListener)
}