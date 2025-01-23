package app.termora

interface TerminalTabbedManager {
    fun addTerminalTab(tab: TerminalTab)
    fun addTerminalTab(index: Int, tab: TerminalTab)
    fun getSelectedTerminalTab(): TerminalTab?
    fun getTerminalTabs(): List<TerminalTab>
    fun setSelectedTerminalTab(tab: TerminalTab)
    fun closeTerminalTab(tab: TerminalTab, disposable: Boolean = true)
}