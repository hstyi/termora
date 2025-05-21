package app.termora

import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.protocol.ProtocolProvider
import app.termora.protocol.ProtocolTestRequester
import app.termora.protocol.ProtocolTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
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

                swingCoroutineScope.launch(Dispatchers.IO) {
                    // 因为测试连接的时候从数据库读取会导致失效，所以这里生成随机ID
                    testConnection(evt, pane.getHost().copy(id = randomUUID()))
                    withContext(Dispatchers.Swing) {
                        putValue(NAME, I18n.getString("termora.new-host.test-connection"))
                        isEnabled = true
                    }
                }
            }
        }
    }


    private suspend fun testConnection(evt: AnActionEvent, host: Host) {
        val owner = this
        val provider = ProtocolProvider.providers.firstOrNull {
            StringUtils.equalsIgnoreCase(
                it.getProtocol(),
                host.protocol
            )
        } ?: return

        if (provider !is ProtocolTester) return

        val requester = ProtocolTestRequester(host, owner)
        if (provider.canTestConnection(requester).not()) return

        try {
            provider.testConnection(requester)
        } catch (e: Exception) {
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(
                    owner, ExceptionUtils.getMessage(e),
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


    override fun doOKAction() {
        if (!pane.validateFields()) {
            return
        }
        host = pane.getHost()
        super.doOKAction()
    }


}