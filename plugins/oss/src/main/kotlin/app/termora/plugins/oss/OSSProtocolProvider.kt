package app.termora.plugins.oss

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import com.aliyun.oss.common.auth.CredentialsProviderFactory
import com.aliyun.oss.model.Bucket
import org.apache.commons.lang3.StringUtils


class OSSProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { OSSProtocolProvider() }
        const val PROTOCOL = "OSS"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.aliyun
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val host = requester.host
        val accessKeyId = host.username
        val secretAccessKey = host.authentication.password

        val credential = CredentialsProviderFactory.newDefaultCredentialProvider(accessKeyId, secretAccessKey)

        // 通过默认的接口获取桶列表
        val oss = OSSClientHandler.createCOSClient(
            credential, "oss-cn-hangzhou.aliyuncs.com",
            StringUtils.EMPTY, host.proxy
        )


        val buckets: List<Bucket>

        try {
            buckets = oss.listBuckets()
        } finally {
            oss.shutdown()
        }

        val defaultPath = host.options.sftpDefaultDirectory
        val fs = OSSFileSystem(OSSClientHandler(host.host, credential, host.proxy, buckets))
        return PathHandler(fs, fs.getPath(defaultPath))

    }


}