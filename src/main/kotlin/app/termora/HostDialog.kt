package app.termora

import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.keyboardinteractive.TerminalUserInteraction
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import javax.swing.*
import kotlin.time.Duration.Companion.minutes

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
        val serialComm = host.options.serialComm
        val serialPort = SerialPort.getCommPort(serialComm.port)
        serialPort.setBaudRate(serialComm.baudRate)
        serialPort.setNumDataBits(serialComm.dataBits)
        when (serialComm.parity) {
            SerialCommParity.None -> serialPort.setParity(SerialPort.NO_PARITY)
            SerialCommParity.Mark -> serialPort.setParity(SerialPort.MARK_PARITY)
            SerialCommParity.Even -> serialPort.setParity(SerialPort.EVEN_PARITY)
            SerialCommParity.Odd -> serialPort.setParity(SerialPort.ODD_PARITY)
            SerialCommParity.Space -> serialPort.setParity(SerialPort.SPACE_PARITY)
        }
        when (serialComm.stopBits) {
            "1" -> serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT)
            "1.5" -> serialPort.setNumStopBits(SerialPort.ONE_POINT_FIVE_STOP_BITS)
            "2" -> serialPort.setNumStopBits(SerialPort.TWO_STOP_BITS)
        }
        when (serialComm.flowControl) {
            SerialCommFlowControl.None -> serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            SerialCommFlowControl.RTS_CTS -> serialPort.setFlowControl(SerialPort.FLOW_CONTROL_RTS_ENABLED or SerialPort.FLOW_CONTROL_CTS_ENABLED)
            SerialCommFlowControl.XON_XOFF -> serialPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED or SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED)
        }
        try {
            if (!serialPort.openPort(1.minutes.inWholeMilliseconds.toInt())) {
                throw IllegalStateException("Open serial port [${serialComm.port}] timeout")
            }
        } finally {
            serialPort.closePort()
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