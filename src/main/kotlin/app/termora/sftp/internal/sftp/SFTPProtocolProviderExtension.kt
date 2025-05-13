package app.termora.sftp.internal.sftp

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class SFTPProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { SFTPProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SFTPTransferProtocolProvider.instance
    }
}