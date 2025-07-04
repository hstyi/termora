package app.termora.transfer.internal.local

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class LocalProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { LocalProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return LocalTransferProtocolProvider.instance
    }
}