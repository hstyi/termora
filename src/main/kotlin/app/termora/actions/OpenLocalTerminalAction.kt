package app.termora.actions

import app.termora.*

class OpenLocalTerminalAction : AnAction(
    I18n.getString("termora.find-everywhere.quick-command.local-terminal"),
    Icons.terminal
) {
    companion object {
        const val LOCAL_TERMINAL = "OpenLocalTerminal"
    }


    init {
        putValue(SHORT_DESCRIPTION, "Open Local Terminal")
        putValue(ACTION_COMMAND_KEY, LOCAL_TERMINAL)
    }


    override fun actionPerformed(evt: AnActionEvent) {
        ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST)?.actionPerformed(
            OpenHostActionEvent(
                evt.source,
                Host(
                    name = name,
                    protocol = Protocol.Local
                ),
                evt
            )
        )
        evt.consume()
    }


}