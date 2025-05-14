package app.termora.protocol

/**
 * 协议
 */
interface ProtocolTester {

    /**
     * 测试连通性，有问题抛异常
     */
    fun testConnection(requester: ProtocolTestRequester) {}

    /**
     * 是否可以测试连接
     */
    fun canTestConnection(requester: ProtocolTestRequester): Boolean = false

}