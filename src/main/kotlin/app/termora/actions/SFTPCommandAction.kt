package app.termora.actions

import app.termora.HostTerminalTab
import app.termora.I18n
import app.termora.OpenHostActionEvent
import app.termora.Protocol

class SFTPCommandAction : AnAction() {
    companion object {
        /**
         * 打开 SFTP command
         */
        const val SFTP_COMMAND = "SFTPCommandAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, SFTP_COMMAND)
        putValue(SHORT_DESCRIPTION, I18n.getString("termora.actions.open-sftp-command"))
    }

    override fun actionPerformed(evt: AnActionEvent) {
        val actionManager = ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST) ?: return
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return
        val tab = terminalTabbedManager.getSelectedTerminalTab() as? HostTerminalTab ?: return
        val host = tab.host
        if (!(host.protocol == Protocol.SSH || host.protocol == Protocol.SFTPPty)) return
        actionManager.actionPerformed(OpenHostActionEvent(evt.source, host.copy(protocol = Protocol.SFTPPty), evt))
        evt.consume()
    }
}