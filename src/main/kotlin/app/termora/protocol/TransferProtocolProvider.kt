package app.termora.protocol

import app.termora.Host
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.provider.FileProvider

interface TransferProtocolProvider : ProtocolProvider {

    /**
     * 获取文件提供者
     */
    fun getFileProvider(): FileProvider

    /**
     * 获取根文件
     */
    fun getRootFileObject(host: Host): FileObject

    override fun isTransfer(): Boolean {
        return true
    }
}