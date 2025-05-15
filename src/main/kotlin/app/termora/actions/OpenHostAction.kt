package app.termora.actions

import app.termora.OpenHostActionEvent
import app.termora.OptionPane
import app.termora.PtyHostTerminalTab
import app.termora.TerminalTab
import app.termora.protocol.GenericProtocolProvider
import app.termora.protocol.ProtocolProvider
import org.apache.commons.lang3.StringUtils
import javax.swing.JOptionPane

class OpenHostAction : AnAction() {
    companion object {
        /**
         * 打开一个主机
         */
        const val OPEN_HOST = "OpenHostAction"
    }


    override fun actionPerformed(evt: AnActionEvent) {
        if (evt !is OpenHostActionEvent) return
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val windowScope = evt.getData(DataProviders.WindowScope) ?: return
        val host = evt.host

        var tab: TerminalTab? = null
        val providers = ProtocolProvider.providers.filterIsInstance<GenericProtocolProvider>()

        if (providers.none { StringUtils.equalsIgnoreCase(it.getProtocol(), host.protocol) }) {
            OptionPane.showMessageDialog(
                windowScope.window,
                "Protocol ${host.protocol} not supported",
                messageType = JOptionPane.ERROR_MESSAGE,
            )
            return
        }

        for (provider in providers) {
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