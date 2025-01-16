package app.termora

class EditHostOptionsPane(private val host: Host) : HostOptionsPane() {
    init {
        generalOption.portTextField.value = host.port
        generalOption.nameTextField.text = host.name
        generalOption.protocolTypeComboBox.selectedItem = host.protocol
        generalOption.usernameTextField.text = host.username
        generalOption.hostTextField.text = host.host
        generalOption.remarkTextArea.text = host.remark
        generalOption.authenticationTypeComboBox.selectedItem = host.authentication.type
        if (host.authentication.type == AuthenticationType.Password) {
            generalOption.passwordTextField.text = host.authentication.password
        } else if (host.authentication.type == AuthenticationType.PublicKey) {
            generalOption.publicKeyComboBox.selectedItem = host.authentication.password
        }

        proxyOption.proxyTypeComboBox.selectedItem = host.proxy.type
        proxyOption.proxyHostTextField.text = host.proxy.host
        proxyOption.proxyPasswordTextField.text = host.proxy.password
        proxyOption.proxyUsernameTextField.text = host.proxy.username
        proxyOption.proxyPortTextField.value = host.proxy.port
        proxyOption.proxyAuthenticationTypeComboBox.selectedItem = host.proxy.authenticationType

        terminalOption.charsetComboBox.selectedItem = host.options.encoding
        terminalOption.environmentTextArea.text = host.options.env
        terminalOption.startupCommandTextField.text = host.options.startupCommand
        terminalOption.heartbeatIntervalTextField.value = host.options.heartbeatInterval

        tunnelingOption.tunnelings.addAll(host.tunnelings)

        if (host.options.jumpHosts.isNotEmpty()) {
            val hosts = HostManager.getInstance().hosts().associateBy { it.id }
            for (id in host.options.jumpHosts) {
                jumpHostsOption.jumpHosts.add(hosts[id] ?: continue)
            }
        }

        jumpHostsOption.filter = { it.id != host.id }
    }

    override fun getHost(): Host {
        val newHost = super.getHost()
        return host.copy(
            name = newHost.name,
            protocol = newHost.protocol,
            host = newHost.host,
            port = newHost.port,
            username = newHost.username,
            authentication = newHost.authentication,
            proxy = newHost.proxy,
            remark = newHost.remark,
            updateDate = System.currentTimeMillis(),
            options = newHost.options,
            tunnelings = newHost.tunnelings,
        )
    }
}