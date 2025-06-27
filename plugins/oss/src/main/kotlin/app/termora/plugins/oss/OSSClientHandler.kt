package app.termora.plugins.oss

import app.termora.AuthenticationType
import app.termora.Proxy
import app.termora.ProxyType
import com.aliyun.oss.ClientBuilderConfiguration
import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.common.auth.CredentialsProvider
import com.aliyun.oss.model.Bucket
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class OSSClientHandler(
    private val endpoint: String,
    private val cred: CredentialsProvider,
    private val proxy: Proxy,
    val buckets: List<Bucket>
) : Closeable {

    companion object {
        fun createCOSClient(
            cred: CredentialsProvider,
            endpoint: String,
            region: String,
            proxy: Proxy
        ): OSS {
            val configuration = ClientBuilderConfiguration()

            if (proxy.type == ProxyType.HTTP) {
                configuration.proxyHost = proxy.host
                configuration.proxyPort = proxy.port
                if (proxy.authenticationType == AuthenticationType.Password) {
                    configuration.proxyPassword = proxy.password
                    configuration.proxyUsername = proxy.username
                }
            }

            var newEndpoint = endpoint
            if ((newEndpoint.startsWith("http://") || newEndpoint.startsWith("https://")).not()) {
                newEndpoint = "https://$endpoint"
            }

            val builder = OSSClientBuilder.create()
                .endpoint(newEndpoint)
                .credentialsProvider(cred)

            if (region.isNotBlank()) {
                builder.region(region)
            }

            return builder
                .clientConfiguration(configuration)
                .build()
        }
    }

    /**
     * key: Region
     * value: Client
     */
    private val clients = mutableMapOf<String, OSS>()
    private val closed = AtomicBoolean(false)

    fun getClientForBucket(bucket: String): OSS {
        if (closed.get()) throw IllegalStateException("Client already closed")

        synchronized(this) {
            val bucket = buckets.first { it.name == bucket }
            if (clients.containsKey(bucket.location)) {
                return clients.getValue(bucket.location)
            }
            clients[bucket.location] = createCOSClient(cred, bucket.extranetEndpoint, bucket.location, proxy)
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