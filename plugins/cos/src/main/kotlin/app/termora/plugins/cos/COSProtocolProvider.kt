package app.termora.plugins.cos

import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.model.Bucket
import org.apache.commons.lang3.StringUtils


class COSProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { COSProtocolProvider() }
        const val PROTOCOL = "COS"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.tencent
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val host = requester.host
        val secretId = host.username
        val secretKey = host.authentication.password
        val cred = BasicCOSCredentials(secretId, secretKey)
        val clientConfig = ClientConfig()

        clientConfig.isPrintShutdownStackTrace = false
        val cosClient = COSClientHandler.createCOSClient(cred, StringUtils.EMPTY, host.proxy)
        val buckets: List<Bucket>

        try {
            buckets = cosClient.listBuckets()
        } finally {
            cosClient.shutdown()
        }

        val defaultPath = host.options.sftpDefaultDirectory
        val fs = COSFileSystem(COSClientHandler(cred, host.proxy, buckets))
        return PathHandler(fs, fs.getPath(defaultPath))
    }


}