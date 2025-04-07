package app.termora

import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.actions.TabReconnectAction
import app.termora.addons.zmodem.ZModemPtyConnectorAdaptor
import app.termora.keymap.KeyShortcut
import app.termora.keymap.KeymapManager
import app.termora.terminal.ControlCharacters
import app.termora.terminal.DataKey
import app.termora.terminal.PtyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.apache.commons.io.Charsets
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.SshConstants
import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.channel.ChannelListener
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.session.SessionListener.Event
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.JComponent
import javax.swing.SwingUtilities


class SSHTerminalTab(windowScope: WindowScope, host: Host) :
    PtyHostTerminalTab(windowScope, host) {
    companion object {
        val SSHSession = DataKey(ClientSession::class)

        private val log = LoggerFactory.getLogger(SSHTerminalTab::class.java)
    }

    private val mutex = Mutex()
    private val tab = this

    private var sshClient: SshClient? = null
    private var sshSession: ClientSession? = null
    private var sshChannelShell: ChannelShell? = null
    private val terminalTabbedManager
        get() = AnActionEvent(getJComponent(), StringUtils.EMPTY, EventObject(getJComponent()))
            .getData(DataProviders.TerminalTabbedManager)

    init {
        terminalPanel.dropFiles = false
        terminalPanel.dataProviderSupport.addData(DataProviders.TerminalTab, this)
    }

    override fun getJComponent(): JComponent {
        return terminalPanel
    }


    override fun canReconnect(): Boolean {
        return !mutex.isLocked
    }


    override suspend fun openPtyConnector(): PtyConnector {
        if (mutex.tryLock()) {
            try {
                return doOpenPtyConnector()
            } finally {
                mutex.unlock()
            }
        }
        throw IllegalStateException("Opening PtyConnector")
    }


    private suspend fun doOpenPtyConnector(): PtyConnector {

        // 连接提示
        withContext(Dispatchers.Swing) {
            // clear screen
            terminal.clearScreen()
            // hide cursor
            terminalModel.setData(DataKey.ShowCursor, false)
            // print
            terminal.write("SSH client is opening...\r\n")
        }

        val owner = SwingUtilities.getWindowAncestor(terminalPanel)
        val client = SshClients.openClient(host, owner).also { sshClient = it }
        val sessionListener = MySessionListener()
        val channelListener = MyChannelListener()

        withContext(Dispatchers.Swing) { terminal.write("SSH client opened successfully.\r\n\r\n") }

        client.addSessionListener(sessionListener)
        client.addChannelListener(channelListener)

        val (session, channel) = try {
            val session = SshClients.openSession(host, client).also { sshSession = it }
            val channel = SshClients.openShell(
                host,
                terminalPanel.winSize(),
                session
            ).also { sshChannelShell = it }
            Pair(session, channel)
        } finally {
            client.removeSessionListener(sessionListener)
            client.removeChannelListener(channelListener)
        }

        // newline
        withContext(Dispatchers.Swing) {
            terminal.write("\r\n")
        }


        channel.addChannelListener(object : ChannelListener {
            private val reconnectShortcut
                get() = KeymapManager.getInstance().getActiveKeymap()
                    .getShortcut(TabReconnectAction.RECONNECT_TAB).firstOrNull()

            override fun channelClosed(channel: Channel, reason: Throwable?) {
                coroutineScope.launch(Dispatchers.Swing) {
                    terminal.write("\r\n\r\n${ControlCharacters.ESC}[31m")
                    terminal.write(I18n.getString("termora.terminal.channel-disconnected"))
                    if (reconnectShortcut is KeyShortcut) {
                        terminal.write(
                            I18n.getString(
                                "termora.terminal.channel-reconnect",
                                reconnectShortcut.toString()
                            )
                        )
                    }
                    terminal.write("\r\n")
                    terminal.write("${ControlCharacters.ESC}[0m")
                    terminalModel.setData(DataKey.ShowCursor, false)
                    if (Database.getDatabase().terminal.autoCloseTabWhenDisconnected) {
                        terminalTabbedManager?.let { manager ->
                            SwingUtilities.invokeLater {
                                manager.closeTerminalTab(tab, true)
                            }
                        }
                    }
                }
            }
        })

        // 打开隧道
        openTunnelings(session, host)

        // 隐藏提示
        withContext(Dispatchers.Swing) {
            // clear screen
            terminal.clearScreen()
            // show cursor
            terminalModel.setData(DataKey.ShowCursor, true)
        }

        return ptyConnectorFactory.decorate(
            ZModemPtyConnectorAdaptor(
                terminal,
                terminalPanel,
                ChannelShellPtyConnector(
                    channel,
                    charset = Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8)
                )
            )
        )
    }

    private suspend fun openTunnelings(session: ClientSession, host: Host) {
        if (host.tunnelings.isEmpty()) {
            return
        }

        for (tunneling in host.tunnelings) {
            try {
                SshClients.openTunneling(session, host, tunneling)
                withContext(Dispatchers.Swing) {
                    terminal.write("Start [${tunneling.name}] port forwarding successfully.\r\n")
                }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error("Start [${tunneling.name}] port forwarding failed: {}", e.message, e)
                }
                withContext(Dispatchers.Swing) {
                    terminal.write("Start [${tunneling.name}] port forwarding failed: ${e.message}\r\n")
                }
            }

        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == SSHSession) {
            return sshSession as T?
        }
        return super.getData(dataKey)
    }

    override fun stop() {
        if (mutex.tryLock()) {
            try {
                super.stop()

                sshChannelShell?.close(true)
                sshSession?.disableSessionHeartbeat()
                sshSession?.disconnect(SshConstants.SSH2_DISCONNECT_BY_APPLICATION, StringUtils.EMPTY)
                sshSession?.close(true)
                sshClient?.close(true)

                sshChannelShell = null
                sshSession = null
                sshClient = null
            } finally {
                mutex.unlock()
            }
        }
    }

    override fun willBeClose(): Boolean {
        // 保存窗口状态
        terminalPanel.storeVisualWindows(host.id)
        return super.willBeClose()
    }

    private inner class MySessionListener : SessionListener, Disposable {
        override fun sessionEvent(session: Session, event: Event) {
            coroutineScope.launch {
                when (event) {
                    Event.KeyEstablished -> terminal.write("Session Key exchange successful.\r\n")
                    Event.Authenticated -> terminal.write("Session authentication successful.\r\n\r\n")
                    Event.KexCompleted -> terminal.write("Session KEX negotiation successful.\r\n")
                }
            }
        }

        override fun sessionEstablished(session: Session) {
            coroutineScope.launch { terminal.write("Session established.\r\n") }
        }

        override fun sessionCreated(session: Session?) {
            coroutineScope.launch { terminal.write("Session created.\r\n") }
        }


    }

    private inner class MyChannelListener : ChannelListener, Disposable {
        override fun channelOpenSuccess(channel: Channel) {
            coroutineScope.launch { terminal.write("Channel shell opened successfully.\r\n") }
        }

        override fun channelInitialized(channel: Channel) {
            coroutineScope.launch { terminal.write("Channel shell initialization successful.\r\n") }
        }

    }
}