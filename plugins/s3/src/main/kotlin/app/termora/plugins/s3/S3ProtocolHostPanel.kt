package app.termora.plugins.s3

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils

class S3ProtocolHostPanel : ProtocolHostPanel() {
    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = S3ProtocolProvider.PROTOCOL
        )
    }

    override fun validateFields(): Boolean {
        return true
    }
}