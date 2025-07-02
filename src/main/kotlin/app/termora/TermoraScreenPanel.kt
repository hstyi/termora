package app.termora

import app.termora.actions.DataProviders
import app.termora.tree.NewHostTree
import java.awt.BorderLayout
import java.util.*
import javax.swing.JPanel

class TermoraScreenPanel(private val windowScope: WindowScope, private val terminalTabbed: TerminalTabbed) :
    JPanel(BorderLayout()) {
    private val welcomePanel = WelcomePanel()

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        add(terminalTabbed, BorderLayout.CENTER)
        terminalTabbed.addTerminalTab(welcomePanel, true)
    }

    private fun initEvents() {
        Disposer.register(windowScope, welcomePanel)
    }

    fun getHostTree(): NewHostTree {
        return Objects.requireNonNull<NewHostTree>(welcomePanel.getData(DataProviders.Welcome.HostTree))
    }
}