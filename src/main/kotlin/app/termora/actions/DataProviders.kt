package app.termora.actions

import app.termora.terminal.DataKey

object DataProviders {
    val TerminalPanel = DataKey(app.termora.terminal.panel.TerminalPanel::class)
    val Terminal = DataKey(app.termora.terminal.Terminal::class)
    val PtyConnector = DataKey(app.termora.terminal.PtyConnector::class)
    val TerminalTabbed = DataKey(app.termora.TerminalTabbed::class)
    val TerminalTabbedManager = DataKey(app.termora.TerminalTabbedManager::class)
    val TermoraFrame = DataKey(app.termora.TermoraFrame::class)
    val WindowScope = DataKey(app.termora.WindowScope::class)


    object Welcome {
        val HostTree = DataKey(app.termora.HostTree::class)
    }
}