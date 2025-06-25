package app.termora.plugins.s3

import app.termora.AuthenticationType
import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.ProxyType
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import io.minio.MinioClient
import okhttp3.*
import org.apache.commons.lang3.StringUtils
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration

class S3ProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { S3ProtocolProvider() }
        const val PROTOCOL = "S3"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.minio
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val host = requester.host
        val builder = MinioClient.builder()
            .endpoint(host.host)
            .credentials(host.username, host.authentication.password)
        val region = host.options.extras["s3.region"]
        if (StringUtils.isNotBlank(region)) {
            builder.region(region)
        }

        if (host.proxy.type != ProxyType.No) {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(60))

            if (host.proxy.type == ProxyType.HTTP) {
                httpClient.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host.proxy.host, host.proxy.port)))
            } else if (host.proxy.type == ProxyType.SOCKS5) {
                httpClient.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(host.proxy.host, host.proxy.port)))
            }

            if (host.proxy.authenticationType != AuthenticationType.No) {
                httpClient.proxyAuthenticator(object : Authenticator {
                    override fun authenticate(route: Route?, response: Response): Request? {
                        val credential = Credentials.basic(host.proxy.username, host.proxy.password)
                        return response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }

                })
            }

            builder.httpClient(httpClient.build(), true)
        }

//        val delimiter = host.options.extras["s3.delimiter"] ?: "/"
        val defaultPath = host.options.sftpDefaultDirectory
        val minioClient = builder.build()
        val fs = S3FileSystem(minioClient)
        return PathHandler(fs, fs.getPath(defaultPath))
    }


}