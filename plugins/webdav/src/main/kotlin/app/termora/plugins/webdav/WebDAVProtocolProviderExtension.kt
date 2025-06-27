package app.termora.plugins.webdav

import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolProviderExtension

class WebDAVProtocolProviderExtension private constructor() : ProtocolProviderExtension {
    companion object {
        val instance by lazy { WebDAVProtocolProviderExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return WebDAVProtocolProvider.instance
    }
}