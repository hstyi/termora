package app.termora

import app.termora.keymgr.KeyManager
import app.termora.keymgr.OhKeyPairKeyPairProvider
import app.termora.terminal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.io.Charsets
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import java.awt.event.KeyEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class SFTPPtyTerminalTab(windowScope: WindowScope, host: Host) : PtyHostTerminalTab(windowScope, host) {
    private val keyManager by lazy { KeyManager.getInstance() }
    private val tempFiles = mutableListOf<Path>()
    private val passwordDataListener = object : DataListener {
        override fun onChanged(key: DataKey<*>, data: Any) {
            if (key == VisualTerminal.Written && data is String) {

                // 要求输入密码
                val line = terminal.getDocument().getScreenLine(terminal.getCursorModel().getPosition().y)
                if (line.getText().startsWith("${host.username}@${host.host}'s password:")) {

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

    override suspend fun openPtyConnector(): PtyConnector {
        // 删除密码监听
        withContext(Dispatchers.Swing) { terminal.getTerminalModel().removeDataListener(passwordDataListener) }

        val winSize = terminalPanel.winSize()
        val commands = mutableListOf("sftp")

        // known_hosts
        commands.add("-o")
        commands.add("UserKnownHostsFile=${File(Application.getBaseDataDir(), "known_hosts").absolutePath}")

        // Compression
        commands.add("-o")
        commands.add("Compression=yes")

        // port
        commands.add("-P")
        commands.add(host.port.toString())

        // 设置认证信息
        setAuthentication(commands)

        commands.add("${host.username}@${host.host}")

        val ptyConnector = ptyConnectorFactory.createPtyConnector(
            commands.toTypedArray(),
            winSize.rows, winSize.cols,
            host.options.envs(),
            Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8),
        )

        return ptyConnector
    }

    private fun setAuthentication(commands: MutableList<String>) {
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
            terminal.getTerminalModel().addDataListener(passwordDataListener)
        }
    }

    override fun stop() {
        for (path in tempFiles) {
            FileUtils.deleteQuietly(path.toFile())
        }
        tempFiles.clear()
        super.stop()
    }
}