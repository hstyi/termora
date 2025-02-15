package app.termora

import app.termora.keymgr.KeyManager
import app.termora.keymgr.OhKeyPairKeyPairProvider
import app.termora.terminal.*
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.io.Charsets
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.util.net.SshdSocketAddress
import java.awt.event.KeyEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities

class SFTPPtyTerminalTab(windowScope: WindowScope, host: Host) : PtyHostTerminalTab(windowScope, host) {
    private val keyManager by lazy { KeyManager.getInstance() }
    private val tempFiles = mutableListOf<Path>()
    private var sshClient: SshClient? = null
    private var sshSession: ClientSession? = null
    private var lastPasswordReporterDataListener: PasswordReporterDataListener? = null

    companion object {
        val canSupports by lazy {
            val process = if (SystemInfo.isWindows) {
                ProcessBuilder("cmd.exe", "/c", "where", "sftp").start()
            } else {
                ProcessBuilder("which", "sftp").start()
            }
            process.waitFor()
            return@lazy process.exitValue() == 0
        }
    }

    override suspend fun openPtyConnector(): PtyConnector {


        val useJumpHosts = host.options.jumpHosts.isNotEmpty() || host.proxy.type != ProxyType.No
        val commands = mutableListOf("sftp")
        var host = this.host

        // 如果配置了跳板机或者代理，那么通过 SSH 的端口转发到本地
        if (useJumpHosts) {
            host = host.copy(
                tunnelings = listOf(
                    Tunneling(
                        type = TunnelingType.Local,
                        sourceHost = SshdSocketAddress.LOCALHOST_NAME,
                        destinationHost = SshdSocketAddress.LOCALHOST_NAME,
                        destinationPort = host.port,
                    )
                )
            )

            val sshClient = SshClients.openClient(host).apply { sshClient = this }
            val sshSession = SshClients.openSession(host, sshClient).apply { sshSession = this }

            // 打开通道
            for (tunneling in host.tunnelings) {
                val address = SshClients.openTunneling(sshSession, host, tunneling)
                host = host.copy(host = address.hostName, port = address.port)
            }
        }


        if (useJumpHosts) {
            // 打开通道后忽略 key 检查
            commands.add("-o")
            commands.add("StrictHostKeyChecking=no")

            // 不保存 known_hosts
            commands.add("-o")
            commands.add("UserKnownHostsFile=${if (SystemInfo.isWindows) "NUL" else "/dev/null"}")
        } else {
            // known_hosts
            commands.add("-o")
            commands.add("UserKnownHostsFile=${File(Application.getBaseDataDir(), "known_hosts").absolutePath}")
        }


        // Compression
        commands.add("-o")
        commands.add("Compression=yes")

        // port
        commands.add("-P")
        commands.add(host.port.toString())

        // 设置认证信息
        setAuthentication(commands, host)


        val envs = host.options.envs()
        if (envs.containsKey("CurrentDir")) {
            val currentDir = envs.getValue("CurrentDir")
            commands.add("${host.username}@${host.host}:${currentDir}")
        } else {
            commands.add("${host.username}@${host.host}")
        }

        val winSize = terminalPanel.winSize()
        val ptyConnector = ptyConnectorFactory.createPtyConnector(
            commands.toTypedArray(),
            winSize.rows, winSize.cols,
            host.options.envs(),
            Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8),
        )

        return ptyConnector
    }

    private fun setAuthentication(commands: MutableList<String>, host: Host) {
        // 如果通过公钥连接
        if (host.authentication.type == AuthenticationType.PublicKey) {
            val keyPair = keyManager.getOhKeyPair(host.authentication.password)
            if (keyPair != null) {
                val keyPair = OhKeyPairKeyPairProvider.generateKeyPair(keyPair)
                val privateKeyPath = Application.createSubTemporaryDir()
                val privateKeyFile = Files.createTempFile(privateKeyPath, Application.getName(), StringUtils.EMPTY)
                Files.newOutputStream(privateKeyFile)
                    .use { OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, null, null, it) }
                commands.add("-i")
                commands.add(privateKeyFile.toFile().absolutePath)
                tempFiles.add(privateKeyPath)
            }
        } else if (host.authentication.type == AuthenticationType.Password) {
            terminal.getTerminalModel().addDataListener(PasswordReporterDataListener(host).apply {
                lastPasswordReporterDataListener = this
            })
        }
    }

    override fun stop() {
        // 删除密码监听
        lastPasswordReporterDataListener?.let { listener ->
            SwingUtilities.invokeLater { terminal.getTerminalModel().removeDataListener(listener) }
        }

        IOUtils.closeQuietly(sshSession)
        IOUtils.closeQuietly(sshClient)

        tempFiles.removeIf {
            FileUtils.deleteQuietly(it.toFile())
            true
        }

        super.stop()
    }

    private inner class PasswordReporterDataListener(private val host: Host) : DataListener {
        override fun onChanged(key: DataKey<*>, data: Any) {
            if (key == VisualTerminal.Written && data is String) {

                // 要求输入密码
                val line = terminal.getDocument().getScreenLine(terminal.getCursorModel().getPosition().y)
                if (line.getText().trim().trimIndent().startsWith("${host.username}@${host.host}'s password:")) {

                    // 删除密码监听
                    terminal.getTerminalModel().removeDataListener(this)

                    val ptyConnector = getPtyConnector()

                    // password
                    ptyConnector.write(host.authentication.password.toByteArray(ptyConnector.getCharset()))

                    // enter
                    ptyConnector.write(
                        terminal.getKeyEncoder().encode(TerminalKeyEvent(KeyEvent.VK_ENTER))
                            .toByteArray(ptyConnector.getCharset())
                    )
                }

            }
        }
    }
}
