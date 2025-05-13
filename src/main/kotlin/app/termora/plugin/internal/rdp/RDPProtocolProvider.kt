package app.termora.plugin.internal.rdp

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.protocol.GenericProtocolProvider
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.util.*
import javax.swing.JOptionPane
import kotlin.time.Duration.Companion.seconds

internal class RDPProtocolProvider private constructor() : GenericProtocolProvider {
    companion object {
        val instance by lazy { RDPProtocolProvider() }
    }

    override fun getProtocol(): String {
        return "RDP"
    }

    override fun createTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): TerminalTab {
        TODO()
    }

    override fun getIcon(): DynamicIcon {
        return Icons.microsoftWindows
    }

    override fun canCreateTerminalTab(dataProvider: DataProvider, windowScope: WindowScope, host: Host): Boolean {
        openRDP(windowScope, host)
        return false
    }

    private fun openRDP(windowScope: WindowScope, host: Host) {
        if (SystemInfo.isLinux) {
            OptionPane.showMessageDialog(
                windowScope.window,
                "Linux cannot connect to Windows Remote Server, Supported only for macOS and Windows",
                messageType = JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (SystemInfo.isMacOS) {
            if (!FileUtils.getFile("/Applications/Windows App.app").exists()) {
                val option = OptionPane.showConfirmDialog(
                    windowScope.window,
                    "If you want to connect to a Windows Remote Server, You have to install the Windows App",
                    optionType = JOptionPane.OK_CANCEL_OPTION
                )
                if (option == JOptionPane.OK_OPTION) {
                    Application.browse(URI.create("https://apps.apple.com/app/windows-app/id1295203466"))
                }
                return
            }
        }

        val sb = StringBuilder()
        sb.append("full address:s:").append(host.host).append(':').append(host.port).appendLine()
        sb.append("username:s:").append(host.username).appendLine()

        val file = FileUtils.getFile(Application.getTemporaryDir(), UUID.randomUUID().toSimpleString() + ".rdp")
        file.outputStream().use { IOUtils.write(sb.toString(), it, Charsets.UTF_8) }

        if (host.authentication.type == AuthenticationType.Password) {
            val systemClipboard = windowScope.window.toolkit.systemClipboard
            val password = host.authentication.password
            systemClipboard.setContents(StringSelection(password), null)
            // clear password
            swingCoroutineScope.launch(Dispatchers.IO) {
                delay(30.seconds)
                if (systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    if (systemClipboard.getData(DataFlavor.stringFlavor) == password) {
                        systemClipboard.setContents(StringSelection(StringUtils.EMPTY), null)
                    }
                }
            }
        }

        if (SystemInfo.isMacOS) {
            ProcessBuilder("open", file.absolutePath).start()
        } else if (SystemInfo.isWindows) {
            ProcessBuilder("mstsc", file.absolutePath).start()
        }

    }
}