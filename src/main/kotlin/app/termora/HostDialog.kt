package app.termora

import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.keyboardinteractive.TerminalUserInteraction
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import javax.swing.*

class HostDialog(owner: Window, host: Host? = null) : DialogWrapper(owner) {
    private val pane = if (host != null) EditHostOptionsPane(host) else HostOptionsPane()
    var host: Host? = host
        private set

    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.new-host.title")
        setLocationRelativeTo(null)

        init()
    }

    override fun createCenterPanel(): JComponent {
        pane.background = UIManager.getColor("window")

        val panel = JPanel(BorderLayout())
        panel.add(pane, BorderLayout.CENTER)
        panel.background = UIManager.getColor("window")
        panel.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        return panel
    }

    override fun createActions(): List<AbstractAction> {
        return listOf(createOkAction(), createTestConnectionAction(), CancelAction())
    }

    private fun createTestConnectionAction(): AbstractAction {
        return object : AnAction(I18n.getString("termora.new-host.test-connection")) {
            override fun actionPerformed(evt: AnActionEvent) {
                if (!pane.validateFields()) {
                    return
                }

                putValue(NAME, "${I18n.getString("termora.new-host.test-connection")}...")
                isEnabled = false

                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch(Dispatchers.IO) {
                    testConnection(pane.getHost())
                    withContext(Dispatchers.Swing) {
                        putValue(NAME, I18n.getString("termora.new-host.test-connection"))
                        isEnabled = true
                    }
                }
            }
        }
    }


    private suspend fun testConnection(host: Host) {
        val owner = this
        if (host.protocol == Protocol.Local) {
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(owner, I18n.getString("termora.new-host.test-connection-successful"))
            }
            return
        }

        try {
            if (host.protocol == Protocol.SSH) {
                testSSH(host)
            } else if (host.protocol == Protocol.Serial) {
                testSerial(host)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(
                    owner, ExceptionUtils.getRootCauseMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
            return
        }

        withContext(Dispatchers.Swing) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.new-host.test-connection-successful")
            )
        }

    }

    private fun testSSH(host: Host) {
        var client: SshClient? = null
        var session: ClientSession? = null
        try {
            client = SshClients.openClient(host)
            client.userInteraction = TerminalUserInteraction(owner)
            session = SshClients.openSession(host, client)
        } finally {
            session?.close()
            client?.close()
        }
    }

    private fun testSerial(host: Host) {
        Serials.openPort(host).closePort()
    }

    override fun doOKAction() {
        if (!pane.validateFields()) {
            return
        }
        host = pane.getHost()
        super.doOKAction()
    }


}