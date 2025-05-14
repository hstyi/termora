package app.termora.plugin.internal.ssh

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout

class SSHProtocolHostPanel : ProtocolHostPanel() {

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        add(SSHHostOptionsPane(), BorderLayout.CENTER)
    }

    private fun initEvents() {}


    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = SSHProtocolProvider.PROTOCOL
        )
    }

    override fun validateFields(): Boolean {
        return true
    }
}