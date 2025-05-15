package app.termora.plugin.internal.local

import app.termora.Host
import app.termora.HostOptionsPane
import app.termora.protocol.ProtocolHostPanel
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout

internal class LocalProtocolHostPanel : ProtocolHostPanel() {

    private val pane by lazy { LocalHostOptionsPane() }
    init {
        initView()
        initEvents()
    }


    private fun initView() {
        add(pane, BorderLayout.CENTER)
    }

    private fun initEvents() {}


    override fun getHost(): Host {
        return Host(
            name = StringUtils.EMPTY,
            protocol = LocalProtocolProvider.PROTOCOL
        )
    }

    override fun validateFields(): Boolean {
        return pane.validateFields()
    }
}