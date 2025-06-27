package app.termora.plugins.obs

import app.termora.AuthenticationType
import app.termora.Proxy
import app.termora.ProxyType
import com.obs.services.IObsCredentialsProvider
import com.obs.services.ObsClient
import com.obs.services.ObsConfiguration
import com.obs.services.model.ObsBucket
import org.apache.commons.io.IOUtils
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class OBSClientHandler(
    private val cred: IObsCredentialsProvider,
    private val proxy: Proxy,
    val buckets: List<ObsBucket>
) : Closeable {

    companion object {
        fun createOBSClient(
            cred: IObsCredentialsProvider,
            endpoint: String,
            proxy: Proxy
        ): ObsClient {
            val configuration = ObsConfiguration()

            if (proxy.type == ProxyType.HTTP) {
                if (proxy.authenticationType == AuthenticationType.Password) {
                    configuration.setHttpProxy(proxy.host, proxy.port, proxy.username, proxy.password)
                } else {
                    configuration.setHttpProxy(proxy.host, proxy.port, null, null)
                }
            }


            var newEndpoint = endpoint
            if ((newEndpoint.startsWith("http://") || newEndpoint.startsWith("https://")).not()) {
                newEndpoint = "https://$endpoint"
            }

            configuration.endPoint = newEndpoint

            val obsClient = ObsClient(cred, configuration)


            return obsClient
        }
    }

    /**
     * key: Region
     * value: Client
     */
    private val clients = mutableMapOf<String, ObsClient>()
    private val closed = AtomicBoolean(false)

    fun getClientForBucket(bucket: String): ObsClient {
        if (closed.get()) throw IllegalStateException("Client already closed")

        synchronized(this) {
            val bucket = buckets.first { it.bucketName == bucket }
            if (clients.containsKey(bucket.location)) {
                return clients.getValue(bucket.location)
            }
            clients[bucket.location] = createOBSClient(cred, "https://obs.${bucket.location}.myhuaweicloud.com", proxy)
            return clients.getValue(bucket.location)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(this) {
                clients.forEach { IOUtils.closeQuietly(it.value) }
                clients.clear()
            }
        }
    }


}