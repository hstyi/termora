package app.termora.transfer

import app.termora.HostManager
import app.termora.HostTerminalTab
import app.termora.I18n
import app.termora.Icons
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.lang3.StringUtils

class TransferAnAction : AnAction(I18n.getString("termora.transport.sftp"), Icons.folder) {
    private val hostManager get() = HostManager.getInstance()
    override fun actionPerformed(evt: AnActionEvent) {
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return

        var sftpTab: TransportTerminalTab? = null
        for (tab in terminalTabbedManager.getTerminalTabs()) {
            if (tab is TransportTerminalTab) {
                sftpTab = tab
                break
            }
        }

        // 创建一个新的
        if (sftpTab == null) {
            sftpTab = TransportTerminalTab()
            terminalTabbedManager.addTerminalTab(sftpTab, false)
        }

        var hostId = if (evt is TransferActionEvent) evt.hostId else StringUtils.EMPTY

        // 如果不是特定事件，那么尝试获取选中的Tab，如果是一个 Host 并且是 SSH 协议那么直接打开
        if (hostId.isBlank()) {
            val tab = terminalTabbedManager.getSelectedTerminalTab()
            if (tab is HostTerminalTab) {
                if (TransferProtocolProvider.valueOf(tab.host.protocol) != null) {
                    hostId = tab.host.id
                }
            }
        }

        terminalTabbedManager.setSelectedTerminalTab(sftpTab)

        if (hostId.isBlank()) return

        val tabbed = sftpTab.rightTabbed
        // 如果已经打开了 那么直接选中
        for (i in 0 until tabbed.tabCount) {
            val panel = tabbed.getTransportPanel(i) ?: continue
            if (panel.host.id == hostId) {
                tabbed.selectedIndex = i
                return
            }
        }

        val host = hostManager.getHost(hostId) ?: return
        for (i in 0 until tabbed.tabCount) {
            val c = tabbed.getComponentAt(i)
            if (c is TransportSelectionPanel) {
                if (c.state == TransportSelectionPanel.State.Initialized) {
                    c.connect(host)
                    return
                }
            }
        }

        tabbed.addSelectionTab()

    }
}