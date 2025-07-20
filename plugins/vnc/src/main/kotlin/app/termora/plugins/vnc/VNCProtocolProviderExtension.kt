package app.termora.plugins.vnc

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class VNCProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance = VNCProtocolProviderExtension()
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return VNCProtocolProvider.instance
    }
}