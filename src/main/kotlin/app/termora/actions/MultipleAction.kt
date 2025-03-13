package app.termora.actions

import app.termora.I18n
import app.termora.Icons
import app.termora.TerminalPanelFactory
import app.termora.WindowScope

class MultipleAction private constructor() : AnAction(
    I18n.getString("termora.tools.multiple"),
    Icons.vcs
) {

    companion object {

        /**
         * 将命令发送到多个会话
         */
        const val MULTIPLE = "MultipleAction"

        fun getInstance(windowScope: WindowScope): MultipleAction {
            return windowScope.getOrCreate(MultipleAction::class) { MultipleAction() }
        }
    }

    init {
        setStateAction()
    }

    override fun actionPerformed(evt: AnActionEvent) {
        TerminalPanelFactory.getInstance().repaintAll()
    }
}