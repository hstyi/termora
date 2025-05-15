package app.termora.plugin.internal.rdp

import app.termora.Host
import app.termora.HostOptionsPane
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout

class RDPProtocolHostPanel : ProtocolHostPanel() {
    private val pane by lazy { RDPHostOptionsPane() }

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        add(pane, BorderLayout.CENTER)
    }

    private fun initEvents() {}


    override fun getHost(): Host {
        return pane.getHost()
    }

    override fun setHost(host: Host) {
       pane.setHost(host)
    }

    override fun validateFields(): Boolean {
        return pane.validateFields()
    }
}