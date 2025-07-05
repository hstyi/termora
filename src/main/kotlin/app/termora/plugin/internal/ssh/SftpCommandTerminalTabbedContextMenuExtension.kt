package app.termora.plugin.internal.ssh

import app.termora.*
import app.termora.actions.*
import app.termora.plugin.internal.sftppty.SFTPPtyProtocolProvider
import app.termora.plugin.internal.sftppty.SFTPPtyTerminalTab
import app.termora.terminal.DataKey
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.swing.Action
import javax.swing.JMenuItem
import javax.swing.JOptionPane

class SftpCommandTerminalTabbedContextMenuExtension private constructor() : TerminalTabbedContextMenuExtension {
    companion object {
        val instance = SftpCommandTerminalTabbedContextMenuExtension()
    }

    private val actionManager = ActionManager.getInstance()

    override fun createJMenuItem(
        windowScope: WindowScope,
        tab: TerminalTab
    ): JMenuItem {
        if (tab is HostTerminalTab) {
            val openHostAction = actionManager.getAction(OpenHostAction.OPEN_HOST)
            if (openHostAction != null) {
                if (tab.host.protocol == SSHProtocolProvider.PROTOCOL || tab.host.protocol == SFTPPtyProtocolProvider.PROTOCOL) {
                    val sftpCommand = JMenuItem(I18n.getString("termora.tabbed.contextmenu.sftp-command"))
                    sftpCommand.addActionListener(object : AnAction() {
                        override fun actionPerformed(evt: AnActionEvent) {
                            openSFTPPtyTab(tab, openHostAction, evt)
                        }
                    })
                    return sftpCommand
                }
            }
        }
        throw UnsupportedOperationException()
    }

    private fun openSFTPPtyTab(tab: HostTerminalTab, openHostAction: Action, evt: EventObject) {
        if (SFTPPtyTerminalTab.canSupports.not()) {
            OptionPane.showMessageDialog(
                tab.windowScope.window,
                I18n.getString("termora.tabbed.contextmenu.sftp-not-install"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            return
        }

        var host = tab.host

        if (host.protocol == SSHProtocolProvider.PROTOCOL) {
            val envs = tab.host.options.envs().toMutableMap()
            val currentDir = tab.getData(DataProviders.Terminal)?.getTerminalModel()
                ?.getData(DataKey.CurrentDir, StringUtils.EMPTY) ?: StringUtils.EMPTY

            if (currentDir.isNotBlank()) {
                envs["CurrentDir"] = currentDir
            }

            host = host.copy(
                protocol = SFTPPtyProtocolProvider.PROTOCOL,
                options = host.options.copy(env = envs.toPropertiesString())
            )
        }

        openHostAction.actionPerformed(OpenHostActionEvent(evt.source, host, evt))
    }

}