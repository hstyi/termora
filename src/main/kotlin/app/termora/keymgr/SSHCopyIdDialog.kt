package app.termora.keymgr

import app.termora.*
import app.termora.keyboardinteractive.TerminalUserInteraction
import app.termora.terminal.ControlCharacters
import app.termora.terminal.DataKey
import app.termora.terminal.PtyConnectorDelegate
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.IOUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Window
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.*
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.UIManager

class SSHCopyIdDialog(
    owner: Window,
    private val windowScope: WindowScope,
    private val hosts: List<Host>,
    private val publicKeys: List<String>,
) : DialogWrapper(owner) {

    companion object {
        private val log = LoggerFactory.getLogger(SSHCopyIdDialog::class.java)
    }

    private val terminalPanelFactory = TerminalPanelFactory.getInstance(windowScope)
    private val terminal by lazy {
        TerminalFactory.getInstance(windowScope).createTerminal().apply {
            getTerminalModel().setData(DataKey.ShowCursor, false)
            getTerminalModel().setData(DataKey.AutoNewline, true)
        }
    }
    private val terminalPanel by lazy {
        terminalPanelFactory.createTerminalPanel(terminal, PtyConnectorDelegate())
    }
    private val coroutineScope = CoroutineScope(Job() + Dispatchers.IO)

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 100, UIManager.getInt("Dialog.height") - 100)
        isModal = true
        title = "SSH Copy ID"
        setLocationRelativeTo(null)

        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                coroutineScope.cancel()
                terminal.close()
                Disposer.dispose(terminalPanel)
            }
        })

        init()
    }

    override fun createCenterPanel(): JComponent {
        return terminalPanel
    }

    fun start() {
        coroutineScope.launch {
            try {
                doStart()
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
        isVisible = true
    }


    override fun createActions(): List<AbstractAction> {
        return listOf(CancelAction())
    }

    private fun magenta(text: Any): String {
        return "${ControlCharacters.ESC}[35m${text}${ControlCharacters.ESC}[0m"
    }

    private fun cyan(text: Any): String {
        return "${ControlCharacters.ESC}[36m${text}${ControlCharacters.ESC}[0m"
    }

    private fun red(text: Any): String {
        return "${ControlCharacters.ESC}[31m${text}${ControlCharacters.ESC}[0m"
    }

    private fun green(text: Any): String {
        return "${ControlCharacters.ESC}[32m${text}${ControlCharacters.ESC}[0m"
    }

    private suspend fun doStart() {
        withContext(Dispatchers.Swing) {
            terminal.write(
                I18n.getString(
                    "termora.keymgr.ssh-copy-id.number",
                    magenta(hosts.size),
                    magenta(publicKeys.size)
                )
            )
            terminal.getDocument().newline()
            terminal.getDocument().newline()
        }

        var myClient: SshClient? = null
        var mySession: ClientSession? = null
        val timeout = Duration.ofMinutes(1)

        for (index in hosts.indices) {
            if (!coroutineScope.isActive) {
                return
            }

            val host = hosts[index]
            withContext(Dispatchers.Swing) {
                terminal.write("[${cyan(index + 1)}/${cyan(hosts.size)}] ${host.name}")
                terminal.getDocument().newline()
            }

            for (j in publicKeys.indices) {
                if (!coroutineScope.isActive) {
                    return
                }

                val publicKey = publicKeys[j]

                withContext(Dispatchers.Swing) {
                    terminal.write("\t[${cyan(j + 1)}/${cyan(publicKeys.size)}] ${I18n.getString("termora.transport.sftp.connecting")}")
                }

                try {
                    val client = SshClients.openClient(host).apply { myClient = this }
                    client.userInteraction = TerminalUserInteraction(owner)
                    val session = SshClients.openSession(host, client).apply { mySession = this }
                    val channel =
                        session.createExecChannel("mkdir -p ~/.ssh && grep -qxF \"$publicKey\" ~/.ssh/authorized_keys || echo \"$publicKey\" >> ~/.ssh/authorized_keys")
                    val baos = ByteArrayOutputStream()
                    channel.out = baos
                    if (channel.open().verify(timeout).await(timeout)) {
                        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeout);
                    }
                    if (channel.exitStatus != 0) {
                        throw IllegalStateException("Server response: ${channel.exitStatus}")
                    }
                    withContext(Dispatchers.Swing) {
                        terminal.getDocument().eraseInLine(2)
                        terminal.write("\r\t[${cyan(j + 1)}/${cyan(publicKeys.size)}] ${green(I18n.getString("termora.keymgr.ssh-copy-id.successful"))}")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Swing) {
                        terminal.getDocument().eraseInLine(2)
                        terminal.write("\r\t[${cyan(j + 1)}/${cyan(publicKeys.size)}] ${red("${I18n.getString("termora.keymgr.ssh-copy-id.failed")}: ${e.message}")}")
                    }
                } finally {
                    IOUtils.closeQuietly(mySession)
                    IOUtils.closeQuietly(myClient)
                }



                withContext(Dispatchers.Swing) {
                    terminal.getDocument().newline()
                }
            }


            withContext(Dispatchers.Swing) {
                terminal.getDocument().newline()
            }
        }


        withContext(Dispatchers.Swing) {
            terminal.write(I18n.getString("termora.keymgr.ssh-copy-id.end"))
        }
    }

}