package app.termora

import app.termora.actions.AnActionEvent
import app.termora.actions.DataProvider
import app.termora.actions.DataProviders
import app.termora.terminal.ControlCharacters
import app.termora.terminal.DataKey
import app.termora.terminal.DataListener
import app.termora.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.swing.Icon

abstract class HostTerminalTab(
    val windowScope: WindowScope,
    val host: Host,
    protected val terminal: Terminal = TerminalFactory.getInstance().createTerminal()
) : PropertyTerminalTab(), DataProvider {
    companion object {
        val Host = DataKey(app.termora.Host::class)
    }


    protected val terminalTabbedManager
        get() = AnActionEvent(getJComponent(), StringUtils.EMPTY, EventObject(getJComponent()))
            .getData(DataProviders.TerminalTabbedManager)
    protected val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    protected val terminalModel get() = terminal.getTerminalModel()


    /*    visualTerminal    */
    protected fun Terminal.clearScreen() {
        this.write("${ControlCharacters.ESC}[3J")
    }

    init {
        terminal.getTerminalModel().setData(Host, host)
        terminal.getTerminalModel().addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
            }
        })
    }

    open fun start() {}

    override fun getTitle(): String {
        return host.name
    }

    override fun getIcon(): Icon {
        return Icons.terminal
    }

    override fun dispose() {
        terminal.close()
        coroutineScope.cancel()
    }

    override fun onGrabFocus() {
        super.onGrabFocus()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == DataProviders.Terminal) {
            return terminal as T?
        }
        return null
    }
}