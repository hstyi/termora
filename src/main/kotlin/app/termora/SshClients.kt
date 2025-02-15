package app.termora

import app.termora.keymgr.OhKeyPairKeyPairProvider
import app.termora.terminal.TerminalSize
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.config.hosts.HostConfigEntry
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver
import org.apache.sshd.client.config.hosts.KnownHostEntry
import org.apache.sshd.client.kex.DHGClient
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier
import org.apache.sshd.client.keyverifier.ModifiedServerKeyAcceptor
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.SshException
import org.apache.sshd.common.channel.PtyChannelConfiguration
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.global.KeepAliveHandler
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.forward.RejectAllForwardingFilter
import org.eclipse.jgit.internal.transport.sshd.JGitClientSession
import org.eclipse.jgit.internal.transport.sshd.JGitSshClient
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.sshd.IdentityPasswordProvider
import org.eclipse.jgit.transport.sshd.ProxyData
import org.slf4j.LoggerFactory
import java.awt.Window
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.math.max

object SshClients {
    private val timeout = Duration.ofSeconds(30)
    private val log by lazy { LoggerFactory.getLogger(SshClients::class.java) }

    /**
     * 打开一个 Shell
     */
    fun openShell(
        host: Host,
        size: TerminalSize,
        session: ClientSession,
    ): ChannelShell {


        val configuration = PtyChannelConfiguration()
        configuration.ptyColumns = size.cols
        configuration.ptyLines = size.rows
        configuration.ptyType = "xterm-256color"

        val env = mutableMapOf<String, String>()
        env["TERM"] = configuration.ptyType
        env.putAll(host.options.envs())

        val channel = session.createShellChannel(configuration, env)
        if (!channel.open().verify(timeout).await()) {
            throw SshException("Failed to open Shell")
        }

        return channel

    }

    /**
     * 打开一个会话
     */
    fun openSession(host: Host, client: SshClient): ClientSession {


        // 如果没有跳板机直接连接
        if (host.options.jumpHosts.isEmpty()) {
            return doOpenSession(host, client)
        }

        val jumpHosts = mutableListOf<Host>()
        val hosts = HostManager.getInstance().hosts().associateBy { it.id }
        for (jumpHostId in host.options.jumpHosts) {
            val e = hosts[jumpHostId]
            if (e == null) {
                if (log.isWarnEnabled) {
                    log.warn("Failed to find jump host: $jumpHostId")
                }
                continue
            }
            jumpHosts.add(e)
        }

        // 最后一跳是目标机器
        jumpHosts.add(host)

        val sessions = mutableListOf<ClientSession>()
        for (i in 0 until jumpHosts.size) {
            val currentHost = jumpHosts[i]
            sessions.add(doOpenSession(currentHost, client, i != 0))

            // 如果有下一跳
            if (i < jumpHosts.size - 1) {
                val nextHost = jumpHosts[i + 1]
                // 通过 currentHost 的 Session 将远程端口映射到本地
                val address = sessions.last().startLocalPortForwarding(
                    SshdSocketAddress.LOCALHOST_ADDRESS,
                    SshdSocketAddress(nextHost.host, nextHost.port),
                )
                if (log.isInfoEnabled) {
                    log.info("jump host: ${currentHost.host}:${currentHost.port} , next host: ${nextHost.host}:${nextHost.port} , local address: ${address.hostName}:${address.port}")
                }
                // 映射完毕之后修改Host和端口
                jumpHosts[i + 1] = nextHost.copy(host = address.hostName, port = address.port)
            }
        }

        return sessions.last()
    }

    fun isMiddleware(session: ClientSession): Boolean {
        if (session is JGitClientSession) {
            if (session.hostConfigEntry.properties["Middleware"]?.toBoolean() == true) {
                return true
            }
        }
        return false
    }


    /**
     * @param middleware 如果为 true 表示是跳板
     */
    private fun doOpenSession(host: Host, client: SshClient, middleware: Boolean = false): ClientSession {
        val entry = HostConfigEntry()
        entry.port = host.port
        entry.username = host.username
        entry.hostName = host.host
        entry.setProperty("Middleware", middleware.toString())

        val session = client.connect(entry)
            .verify(timeout).session
        if (host.authentication.type == AuthenticationType.Password) {
            session.addPasswordIdentity(host.authentication.password)
        } else if (host.authentication.type == AuthenticationType.PublicKey) {
            session.keyIdentityProvider = OhKeyPairKeyPairProvider(host.authentication.password)
        }

        val verifyTimeout = Duration.ofSeconds(timeout.seconds * 5)
        if (!session.auth().verify(verifyTimeout).await(verifyTimeout)) {
            throw SshException("Authentication failed")
        }

        return session
    }

    fun openTunneling(session: ClientSession, host: Host, tunneling: Tunneling): SshdSocketAddress {

        val sshdSocketAddress = if (tunneling.type == TunnelingType.Local) {
            session.startLocalPortForwarding(
                SshdSocketAddress(tunneling.sourceHost, tunneling.sourcePort),
                SshdSocketAddress(tunneling.destinationHost, tunneling.destinationPort)
            )
        } else if (tunneling.type == TunnelingType.Remote) {
            session.startRemotePortForwarding(
                SshdSocketAddress(tunneling.sourceHost, tunneling.sourcePort),
                SshdSocketAddress(tunneling.destinationHost, tunneling.destinationPort),
            )
        } else if (tunneling.type == TunnelingType.Dynamic) {
            session.startDynamicPortForwarding(
                SshdSocketAddress(
                    tunneling.sourceHost,
                    tunneling.sourcePort
                )
            )
        } else {
            SshdSocketAddress.LOCALHOST_ADDRESS
        }

        if (log.isInfoEnabled) {
            log.info(
                "SSH [{}] started {} port forwarding. host: {} , port: {}",
                host.name,
                tunneling.name,
                sshdSocketAddress.hostName,
                sshdSocketAddress.port
            )
        }

        return sshdSocketAddress
    }

    /**
     * 打开一个客户端
     */
    fun openClient(host: Host): SshClient {
        val builder = ClientBuilder.builder()
        builder.globalRequestHandlers(listOf(KeepAliveHandler.INSTANCE))
            .factory { JGitSshClient() }

        val keyExchangeFactories = ClientBuilder.setUpDefaultKeyExchanges(true).toMutableList()

        // https://github.com/TermoraDev/termora/issues/123
        keyExchangeFactories.addAll(
            listOf(
                DHGClient.newFactory(BuiltinDHFactories.dhg1),
                DHGClient.newFactory(BuiltinDHFactories.dhg14),
                DHGClient.newFactory(BuiltinDHFactories.dhgex),
            )
        )
        builder.keyExchangeFactories(keyExchangeFactories)

        if (host.tunnelings.isEmpty() && host.options.jumpHosts.isEmpty()) {
            builder.forwardingFilter(RejectAllForwardingFilter.INSTANCE)
        } else {
            builder.forwardingFilter(AcceptAllForwardingFilter.INSTANCE)
        }

        builder.hostConfigEntryResolver(HostConfigEntryResolver.EMPTY)

        val sshClient = builder.build() as JGitSshClient

        // https://github.com/TermoraDev/termora/issues/180
        // JGit 会尝试读取本地的私钥或缓存的私钥
        sshClient.keyIdentityProvider = KeyIdentityProvider { mutableListOf() }

        val heartbeatInterval = max(host.options.heartbeatInterval, 3)
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(sshClient, Duration.ofSeconds(heartbeatInterval.toLong()))
        CoreModuleProperties.ALLOW_DHG1_KEX_FALLBACK.set(sshClient, true)

        sshClient.setKeyPasswordProviderFactory { IdentityPasswordProvider(CredentialsProvider.getDefault()) }

        if (host.proxy.type != ProxyType.No) {
            sshClient.setProxyDatabase {
                if (host.proxy.authenticationType == AuthenticationType.No) ProxyData(
                    Proxy(
                        if (host.proxy.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                        InetSocketAddress(host.proxy.host, host.proxy.port)
                    )
                )
                else
                    ProxyData(
                        Proxy(
                            if (host.proxy.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                            InetSocketAddress(host.proxy.host, host.proxy.port)
                        ),
                        host.proxy.username,
                        host.proxy.password.toCharArray(),
                    )
            }
        }

        sshClient.start()
        return sshClient
    }
}


private class MyDialogServerKeyVerifier(private val owner: Window) : ServerKeyVerifier, ModifiedServerKeyAcceptor {
    override fun verifyServerKey(
        clientSession: ClientSession,
        remoteAddress: SocketAddress,
        serverKey: PublicKey
    ): Boolean {

        if (SshClients.isMiddleware(clientSession)) {
            return true
        }

        val result = AtomicBoolean(false)

        SwingUtilities.invokeAndWait {
            result.set(
                OptionPane.showConfirmDialog(
                    parentComponent = owner,
                    message = I18n.getString(
                        "termora.host.verify-server-key",
                        remoteAddress.toString().replace("/", StringUtils.EMPTY),
                        KeyUtils.getKeyType(serverKey),
                        KeyUtils.getFingerPrint(serverKey)
                    ),
                    optionType = JOptionPane.OK_CANCEL_OPTION,
                    messageType = JOptionPane.WARNING_MESSAGE,
                ) == JOptionPane.OK_OPTION
            )
        }

        return result.get()
    }

    override fun acceptModifiedServerKey(
        clientSession: ClientSession?,
        remoteAddress: SocketAddress?,
        entry: KnownHostEntry?,
        expected: PublicKey?,
        actual: PublicKey?
    ): Boolean {
        val result = AtomicBoolean(false)

        SwingUtilities.invokeAndWait {
            result.set(
                OptionPane.showConfirmDialog(
                    parentComponent = owner,
                    message = I18n.getString(
                        "termora.host.modified-server-key",
                        remoteAddress.toString().replace("/", StringUtils.EMPTY),
                        KeyUtils.getKeyType(expected),
                        KeyUtils.getFingerPrint(expected),
                        KeyUtils.getKeyType(actual),
                        KeyUtils.getFingerPrint(actual),
                    ),
                    optionType = JOptionPane.OK_CANCEL_OPTION,
                    messageType = JOptionPane.WARNING_MESSAGE,
                ) == JOptionPane.OK_OPTION
            )
        }

        return result.get()
    }
}

class DialogServerKeyVerifier(
    owner: Window,
) : KnownHostsServerKeyVerifier(
    MyDialogServerKeyVerifier(owner),
    Paths.get(Application.getBaseDataDir().absolutePath, "known_hosts")
) {
    init {
        modifiedServerKeyAcceptor = delegateVerifier as ModifiedServerKeyAcceptor
    }

    override fun updateKnownHostsFile(
        clientSession: ClientSession?,
        remoteAddress: SocketAddress?,
        serverKey: PublicKey?,
        file: Path?,
        knownHosts: Collection<HostEntryPair?>?
    ): KnownHostEntry? {
        if (clientSession is JGitClientSession) {
            if (SshClients.isMiddleware(clientSession)) {
                return null
            }
        }
        return super.updateKnownHostsFile(clientSession, remoteAddress, serverKey, file, knownHosts)
    }
}
