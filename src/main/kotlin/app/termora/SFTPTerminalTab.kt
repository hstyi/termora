package app.termora

import app.termora.actions.DataProvider
import app.termora.terminal.DataKey
import app.termora.transport.TransportDataProviders
import app.termora.transport.TransportPanel
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class SFTPTerminalTab : Disposable, TerminalTab, DataProvider {

    private val sftp get() = Database.getDatabase().sftp
    private val transportPanel = TransportPanel()

    init {
        Disposer.register(this, transportPanel)
    }

    override fun getTitle(): String {
        return "SFTP"
    }

    override fun getIcon(): Icon {
        return Icons.folder
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {

    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun getJComponent(): JComponent {
        return transportPanel
    }

    override fun canClone(): Boolean {
        return false
    }

    override fun canClose(): Boolean {
        assertEventDispatchThread()

        if (sftp.pinTab) {
            return false
        }

        val transportManager = transportPanel.getData(TransportDataProviders.TransportManager) ?: return true
        if (transportManager.getTransports().isEmpty()) {
            return true
        }

        return OptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(getJComponent()),
            I18n.getString("termora.transport.sftp.close-tab"),
            messageType = JOptionPane.QUESTION_MESSAGE,
            optionType = JOptionPane.OK_CANCEL_OPTION
        ) == JOptionPane.OK_OPTION
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == TransportDataProviders.TransportPanel) {
            return transportPanel as T
        }
        return null
    }

}