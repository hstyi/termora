package app.termora.transport

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.apache.commons.lang3.SystemUtils
import org.apache.sshd.sftp.client.SftpClientFactory
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.file.FileSystems
import java.util.function.Supplier
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JSplitPane
import kotlin.io.path.absolutePathString


class TransportViewer : JPanel(BorderLayout()), DataProvider, Disposable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val splitPane = JSplitPane()

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        val host = Host(
            name = "Test",
            protocol = SSHProtocolProvider.PROTOCOL,
            username = "myuser",
            host = "127.0.0.1",
            port = 2224,
            authentication = Authentication.No.copy(
                type = AuthenticationType.Password,
                password = "123456"
            )
        )


        val leftPanel = TransportPanel(
            coroutineScope,
            host,
            object : Supplier<TransportSupport> {
                private val support by lazy {
                    TransportSupport(FileSystems.getDefault(), SystemUtils.USER_HOME)
                }

                override fun get(): TransportSupport {
                    return support
                }
            }
        )

        val rightPanel = TransportPanel(
            coroutineScope,
            host,
            object : Supplier<TransportSupport> {
                private val client by lazy {
                    val client = SshClients.openClient(host)
                    val session = SshClients.openSession(host, client)
                    val fileSystem = SftpClientFactory.instance().createSftpFileSystem(session)
                    TransportSupport(fileSystem, fileSystem.defaultDir.absolutePathString())
                }

                override fun get(): TransportSupport {
                    return client
                }
            }
        )

        leftPanel.border = BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor)
        rightPanel.border = BorderFactory.createMatteBorder(0, 1, 0, 0, DynamicColor.BorderColor)

        splitPane.resizeWeight = 0.5
        splitPane.leftComponent = leftPanel
        splitPane.rightComponent = rightPanel
        add(splitPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        splitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                splitPane.setDividerLocation(splitPane.resizeWeight)
            }
        })
    }

}