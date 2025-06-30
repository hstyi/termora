package app.termora.plugins.smb

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class SMBProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { SMBProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return SMBProtocolProvider.instance
    }
}