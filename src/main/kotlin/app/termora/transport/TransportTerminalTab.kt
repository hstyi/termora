package app.termora.transport

import app.termora.Disposer
import app.termora.I18n
import app.termora.Icons
import app.termora.RememberFocusTerminalTab
import app.termora.terminal.DataKey
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent

class TransportTerminalTab : RememberFocusTerminalTab() {
    private val transportViewer = TransportViewer()

    init {
        Disposer.register(this, transportViewer)
    }

    override fun getTitle(): String {
        return I18n.getString("termora.transport.sftp")
    }

    override fun getIcon(): Icon {
        return Icons.folder
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun canClose(): Boolean {
        return true
    }

    override fun getJComponent(): JComponent {
        return transportViewer
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return null
    }
}