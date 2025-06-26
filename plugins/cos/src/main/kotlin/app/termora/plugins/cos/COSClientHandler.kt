package app.termora.plugins.cos

import app.termora.AuthenticationType
import app.termora.Proxy
import app.termora.ProxyType
import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.model.Bucket
import com.qcloud.cos.region.Region
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class COSClientHandler(
    private val cred: BasicCOSCredentials,
    private val proxy: Proxy,
    val buckets: List<Bucket>
) : Closeable {

    companion object {
        fun createCOSClient(cred: BasicCOSCredentials, region: String, proxy: Proxy): COSClient {
            val clientConfig = ClientConfig()
            if (region.isNotBlank()) {
                clientConfig.region = Region(region)
            }
            clientConfig.isPrintShutdownStackTrace = false
            if (proxy.type == ProxyType.HTTP) {
                clientConfig.httpProxyIp = proxy.host
                clientConfig.httpProxyPort = proxy.port
                if (proxy.authenticationType == AuthenticationType.Password) {
                    clientConfig.proxyPassword = proxy.password
                    clientConfig.proxyUsername = proxy.username
                }
            }
            return COSClient(cred, clientConfig)
        }
    }

    /**
     * key: Region
     * value: Client
     */
    private val clients = mutableMapOf<String, COSClient>()
    private val closed = AtomicBoolean(false)

    fun getClientForBucket(bucket: String): COSClient {
        if (closed.get()) throw IllegalStateException("Client already closed")

        synchronized(this) {
            val bucket = buckets.first { it.name == bucket }
            if (clients.containsKey(bucket.location)) {
                return clients.getValue(bucket.location)
            }
            clients[bucket.location] = createCOSClient(cred, bucket.location, proxy)
            return clients.getValue(bucket.location)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(this) {
                clients.forEach { it.value.shutdown() }
                clients.clear()
            }
        }
    }


}