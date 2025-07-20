package app.termora.plugin.internal.updater

import app.termora.*
import app.termora.Application.httpClient
import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.Request
import okhttp3.internal.closeQuietly
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Strings
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.net.io.CopyStreamEvent
import org.apache.commons.net.io.CopyStreamListener
import org.apache.commons.net.io.Util
import org.jdesktop.swingx.JXEditorPane
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Window
import java.net.URI
import java.util.*
import java.util.concurrent.Executors
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import kotlin.math.floor

internal class UpdaterDialog(owner: Window, private val latestVersion: UpdaterManager.LatestVersion) :
    DialogWrapper(owner) {
    companion object {
        private val log = LoggerFactory.getLogger(UpdaterDialog::class.java)
    }

    private enum class State {
        Ready,
        Downloading,
        Downloaded,
    }

    private val progressBar = JProgressBar()
    private val okAction = OkAction(I18n.getString("termora.update.update"))
    private val okButton = JButton(okAction)
    private var state = State.Ready
    private val westSourcePanel = Box.createHorizontalBox()
    private val glue = Box.createHorizontalGlue()
    private val layout = Application.getLayout()
    private val dialog get() = this

    private val executorService = Executors.newVirtualThreadPerTaskExecutor()
    private val coroutineDispatcher = executorService.asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(coroutineDispatcher)

    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        controlsVisible = false
        isResizable = false
        escapeDispose = false
        title = I18n.getString("termora.update.title")
        setLocationRelativeTo(owner)

        init()
        initView()
        initEvents()
    }

    private fun initView() {
        progressBar.isIndeterminate = true
        progressBar.isStringPainted = true

        westSourcePanel.add(glue)
        westSourcePanel.add(progressBar)
        westSourcePanel.add(Box.createHorizontalStrut(20))
        progressBar.isVisible = false
    }

    private fun initEvents() {
        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                coroutineScope.cancel()
                coroutineDispatcher.close()
                executorService.shutdownNow()
            }
        })
    }

    override fun createCenterPanel(): JComponent {
        val editorPane = JXEditorPane()
        editorPane.contentType = "text/html"
        editorPane.text = latestVersion.htmlBody
        editorPane.isEditable = false
        editorPane.addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                Application.browse(it.url.toURI())
            }
        }
        editorPane.background = DynamicColor("window")
        val scrollPane = JScrollPane(editorPane)
        scrollPane.border = BorderFactory.createEmptyBorder()
        return scrollPane
    }

    override fun createWestSourcePanel(): JComponent {
        return westSourcePanel
    }


    override fun createActions(): List<AbstractAction> {
        return listOf(okAction, createCancelAction())
    }

    override fun createJButtonForAction(action: Action): JButton {
        if (action == okAction) {
            rootPane.defaultButton = okButton
            return okButton
        }
        return super.createJButtonForAction(action)
    }

    @Suppress("CascadeIf")
    override fun doOKAction() {
        if (state == State.Ready) {
            okButton.text = "${okButton.text}..."
            okButton.isEnabled = false
            progressBar.isVisible = true
            glue.isVisible = false
            state = State.Downloading

            coroutineScope.launch {
                try {
                    downloadPkg()
                } catch (_: LatestReleaseException) {
                    Application.browse(URI.create("https://github.com/TermoraDev/termora/releases/latest"))
                    SwingUtilities.invokeLater { doCancelAction() }
                } catch (e: Exception) {
                    if (log.isErrorEnabled) log.error(e.message, e)
                    withContext(Dispatchers.Swing) {

                        state = State.Ready
                        okButton.isEnabled = true
                        okButton.text = okAction.name
                        progressBar.isVisible = false
                        glue.isVisible = true

                        OptionPane.showMessageDialog(
                            dialog,
                            StringUtils.defaultIfBlank(e.message, ExceptionUtils.getRootCauseMessage(e)).toString(),
                            messageType = JOptionPane.ERROR_MESSAGE
                        )

                    }
                } finally {
                    withContext(Dispatchers.Swing) {
                        progressBar.isVisible = false
                        glue.isVisible = true
                        okButton.isEnabled = true
                    }
                }
            }
            return
        } else if (state == State.Downloading) {
            return
        } else if (state == State.Downloaded) {
            super.doOKAction()
        }

    }


    private suspend fun downloadPkg() {
        val arch = if (SystemInfo.isAARCH64) "aarch64" else "x86-64"
        val osName = if (SystemInfo.isWindows) "windows" else if (SystemInfo.isLinux) "linux" else "osx"
        val suffix = when (layout) {
            AppLayout.Zip -> "zip"
            AppLayout.Exe -> "exe"

            AppLayout.App -> "dmg"

            AppLayout.TarGz -> "tar.gz"
            AppLayout.AppImage -> "AppImage"
            AppLayout.Deb -> "deb"
            else -> throw LatestReleaseException()
        }

        val filename = "termora-${latestVersion.version}-${osName}-${arch}.${suffix}"
        val asset = latestVersion.assets.find { it.name == filename } ?: throw LatestReleaseException()

        var url = asset.downloadUrl

        if (I18n.isChinaMainland()) {
            url = Strings.CI.replace(
                url,
                "https://github.com/TermoraDev/termora/releases/download/",
                "https://dl.termora.cn/termora/"
            )
        }

        val response = httpClient.newCall(Request.Builder().url(url).get().build())
            .execute()

        if (response.isSuccessful.not()) {
            response.closeQuietly()
            throw IllegalStateException("Failed to download asset $filename")
        }

        withContext(Dispatchers.Swing) {
            progressBar.isIndeterminate = false
        }

        val listener = object : CopyStreamListener {
            override fun bytesTransferred(event: CopyStreamEvent?) {
                TODO("Not yet implemented")
            }

            override fun bytesTransferred(
                totalBytesTransferred: Long,
                bytesTransferred: Int,
                streamSize: Long
            ) {
                SwingUtilities.invokeLater {
                    val progress = 1.0 * totalBytesTransferred / asset.size * 100
                    progressBar.value = floor(progress).toInt()
                    progressBar.string = formatBytes(totalBytesTransferred) + " / " + formatBytes(asset.size)
                }
            }
        }

        val file = FileUtils.getFile(Application.getTemporaryDir(), "${UUID.randomUUID()}-${filename}")

        response.use {
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    Util.copyStream(
                        input, output, Util.DEFAULT_COPY_BUFFER_SIZE,
                        asset.size, listener
                    )
                }
            }
        }

        withContext(Dispatchers.Swing) {
            state = State.Downloaded
        }

        val commands = mutableListOf<String>()
        if (SystemInfo.isMacOS) {
            commands.addAll(listOf("open", "-n", file.absolutePath))
        } else if (layout == AppLayout.Zip) {
            commands.addAll(listOf("explorer", "/select," + file.absolutePath))
        } else if (layout == AppLayout.Exe) {
            // 如果安装过，那么直接静默安装和自动启动
            if (isAppInstalled()) {
                commands.addAll(
                    listOf(
                        file.absolutePath,
                        "/SILENT",
                        "/AUTOSTART",
                        "/NORESTART",
                        "/FORCECLOSEAPPLICATIONS"
                    )
                )
            } else {
                commands.addAll(listOf(file.absolutePath))
            }
        } else if (SystemInfo.isLinux) {
            commands.addAll(listOf("xdg-open", file.parentFile.absolutePath))
        }

        if (log.isInfoEnabled) {
            log.info("commands: {}", commands.joinToString(StringUtils.SPACE))
        }

        SwingUtilities.invokeLater {
            super.doOKAction()
            TermoraRestarter.getInstance().scheduleRestart(owner, true, commands)
        }


    }

    private class LatestReleaseException() : RuntimeException()

    private fun isAppInstalled(): Boolean {
        try {
            val keyPath = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\${Application.getName()}_is1"
            val phkKey = WinReg.HKEYByReference()

            // 尝试打开注册表键
            val result = Advapi32.INSTANCE.RegOpenKeyEx(
                WinReg.HKEY_LOCAL_MACHINE,
                keyPath,
                0,
                WinNT.KEY_READ,
                phkKey
            )

            if (result == WinError.ERROR_SUCCESS) {
                // 键存在，关闭句柄
                Advapi32.INSTANCE.RegCloseKey(phkKey.getValue())
                return true
            } else {
                // 键不存在或无权限
                return false
            }
        } catch (_: Exception) {
            return false
        }
    }

    override fun addNotify() {
        super.addNotify()
    }
}