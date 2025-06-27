package app.termora.plugins.webdav

import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProvider

class WebDAVProtocolHostPanelExtension private constructor() : ProtocolHostPanelExtension {
    companion object {
        val instance by lazy { WebDAVProtocolHostPanelExtension() }
    }

    override fun getProtocolProvider(): ProtocolProvider {
        return WebDAVProtocolProvider.instance
    }

    override fun createProtocolHostPanel(): ProtocolHostPanel {
        return WebDAVProtocolHostPanel()
    }
}