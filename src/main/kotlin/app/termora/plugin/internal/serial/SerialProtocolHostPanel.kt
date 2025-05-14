package app.termora.plugin.internal.serial

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout

class SerialProtocolHostPanel : ProtocolHostPanel() {

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        add(SerialHostOptionsPane(), BorderLayout.CENTER)
    }

    private fun initEvents() {}


    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = SerialProtocolProvider.PROTOCOL
        )
    }

    override fun validateFields(): Boolean {
        return true
    }
}