package app.termora.plugin.internal.telnet

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

internal class TelnetProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance = TelnetProtocolProviderExtension()
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return TelnetProtocolProvider.instance
    }
}