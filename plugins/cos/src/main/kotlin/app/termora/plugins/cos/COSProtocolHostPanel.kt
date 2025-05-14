package app.termora.plugins.cos

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils

class COSProtocolHostPanel : ProtocolHostPanel() {
    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = COSProtocolProvider.Companion.PROTOCOL
        )
    }

    override fun validateFields(): Boolean {
        return true
    }
}