package app.termora.protocol

import org.apache.commons.vfs2.provider.FileProvider

interface TransferProtocolProvider : ProtocolProvider {

    /**
     * 获取文件提供者
     */
    fun getFileProvider(): FileProvider

    override fun isTransfer(): Boolean {
        return true
    }
}