package app.termora

import app.termora.plugin.Extension
import javax.swing.JMenuItem

interface TerminalTabbedContextMenuExtension : Extension {

    /**
     * 抛出 [UnsupportedOperationException] 表示不支持
     */
    fun createJMenuItem(windowScope: WindowScope, tab: TerminalTab): JMenuItem
}