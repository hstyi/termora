package app.termora.actions

import app.termora.OpenHostActionEvent
import app.termora.PtyHostTerminalTab
import app.termora.TerminalTab
import app.termora.plugin.ExtensionManager
import app.termora.protocol.ProtocolProviderExtension
import org.apache.commons.lang3.StringUtils

class OpenHostAction : AnAction() {
    companion object {
        /**
         * 打开一个主机
         */
        const val OPEN_HOST = "OpenHostAction"
    }

    private val protocolProviders
        get() = ExtensionManager.getInstance().getExtensions(ProtocolProviderExtension::class.java)
            .map { it.getProtocolProvider() }

    override fun actionPerformed(evt: AnActionEvent) {
        if (evt !is OpenHostActionEvent) return
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val windowScope = evt.getData(DataProviders.WindowScope) ?: return
        val host = evt.host

        var tab: TerminalTab? = null

        for (provider in protocolProviders) {
            if (StringUtils.equalsIgnoreCase(provider.getProtocol(), host.protocol)) {
                if (provider.canCreateTerminalTab(evt, windowScope, host)) {
                    tab = provider.createTerminalTab(evt, windowScope, host)
                    break
                }
            }
        }

        if (tab == null) return

        terminalTabbedManager.addTerminalTab(tab)
        if (tab is PtyHostTerminalTab) {
            tab.start()
        }

    }


}