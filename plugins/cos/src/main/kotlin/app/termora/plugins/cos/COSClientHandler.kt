package app.termora.plugins.cos

import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.model.Bucket
import com.qcloud.cos.region.Region
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class COSClientHandler(private val cred: BasicCOSCredentials, val buckets: List<Bucket>) : Closeable {
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
            val clientConfig = ClientConfig(Region(bucket.location))
            clientConfig.isPrintShutdownStackTrace = false
            clients[bucket.location] = COSClient(cred, clientConfig)
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