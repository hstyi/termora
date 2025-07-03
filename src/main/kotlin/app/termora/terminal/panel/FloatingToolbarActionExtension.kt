package app.termora.terminal.panel

import app.termora.TerminalTab
import app.termora.actions.AnAction
import app.termora.plugin.Extension
import app.termora.terminal.panel.vw.VisualWindow
import app.termora.terminal.panel.vw.VisualWindowManager

interface FloatingToolbarActionExtension : Extension {

    /**
     * 抛出 [UnsupportedOperationException] 表示不支持
     */
    fun createActionButton(visualWindowManager: VisualWindowManager, tab: TerminalTab): AnAction

    /**
     * 获取要返回的虚拟窗口
     */
    fun getVisualWindowClass(tab: TerminalTab): Class<out VisualWindow>
}