package app.termora.plugin.internal.rdp

import app.termora.Host
import app.termora.HostOptionsPane
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout

class RDPProtocolHostPanel : ProtocolHostPanel() {

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        add(RDPHostOptionsPane(), BorderLayout.CENTER)
    }

    private fun initEvents() {}


    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = RDPProtocolProvider.PROTOCOL
        )
    }

    override fun validateFields(): Boolean {
        return true
    }
}