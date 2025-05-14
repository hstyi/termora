package app.termora.plugins.s3

import app.termora.protocol.FileObjectHandler
import app.termora.protocol.FileObjectRequester
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.vfs2.provider.FileProvider

class S3ProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { S3ProtocolProvider() }
    }

    override fun getProtocol(): String {
        return "S3"
    }

    override fun getFileProvider(): FileProvider {
        return S3FileProvider.instance
    }

    override fun getRootFileObject(requester: FileObjectRequester): FileObjectHandler {
        TODO("Not yet implemented")
    }

}