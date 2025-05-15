package app.termora.plugins.s3

import app.termora.Host
import app.termora.protocol.ProtocolHostPanel
import java.awt.BorderLayout

class S3ProtocolHostPanel : ProtocolHostPanel() {

    private val pane by lazy { S3HostOptionsPane() }

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