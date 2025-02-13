package app.termora.transport

import app.termora.Host
import app.termora.Icons
import app.termora.SFTPTerminalTab
import app.termora.SSHTerminalTab
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders

class SFTPAction : AnAction("SFTP", Icons.folder) {
    override fun actionPerformed(evt: AnActionEvent) {
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val selectedTerminalTab = terminalTabbedManager.getSelectedTerminalTab()
        val host = if (selectedTerminalTab is SSHTerminalTab) selectedTerminalTab.host else null

        val tabs = terminalTabbedManager.getTerminalTabs()
        for (tab in tabs) {
            if (tab is SFTPTerminalTab) {
                terminalTabbedManager.setSelectedTerminalTab(tab)
                if (host != null) {
                    connectHost(host, tab)
                }
                return
            }
        }

        // 创建一个新的
        val tab = SFTPTerminalTab()
        terminalTabbedManager.addTerminalTab(tab)

        if (host != null) {
            connectHost(host, tab)
        }
    }

    /**
     * 如果当前选中的是 SSH 服务器 Tab，那么直接打开 SFTP 通道
     */
    private fun connectHost(host: Host, tab: SFTPTerminalTab) {
        val tabbed = tab.getData(TransportDataProviders.TransportPanel)
            ?.getData(TransportDataProviders.RightFileSystemTabbed) ?: return

        // 如果已经有对应的连接
        for (i in 0 until tabbed.tabCount) {
            val c = tabbed.getComponentAt(i)
            if (c is SftpFileSystemPanel) {
                if (c.host == host) {
                    tabbed.selectedIndex = i
                    return
                }
            }
        }

        // 寻找空的 Tab，如果有则占用
        for (i in 0 until tabbed.tabCount) {
            val c = tabbed.getComponentAt(i)
            if (c is SftpFileSystemPanel) {
                if (c.host == null) {
                    c.host = host
                    c.connect()
                    tabbed.selectedIndex = i
                    return
                }
            }
        }

        // 开启一个新的
        tabbed.addTab(host.name, SftpFileSystemPanel(host).apply { connect() })
    }
}