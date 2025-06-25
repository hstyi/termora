package app.termora.plugins.s3

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import io.minio.MinioClient
import org.apache.commons.lang3.StringUtils

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
//        val delimiter = host.options.extras["s3.delimiter"] ?: "/"
        val defaultPath = host.options.sftpDefaultDirectory
        val minioClient = builder.build()
        val fs = S3FileSystem(minioClient)
        return PathHandler(fs, fs.getPath(defaultPath))
    }


}