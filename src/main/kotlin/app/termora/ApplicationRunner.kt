package app.termora

import app.termora.db.Database
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatSystemProperties
import com.formdev.flatlaf.extras.FlatInspector
import com.formdev.flatlaf.util.SystemInfo
import com.jthemedetecor.OsThemeDetector
import com.mixpanel.mixpanelapi.ClientDelivery
import com.mixpanel.mixpanelapi.MessageBuilder
import com.mixpanel.mixpanelapi.MixpanelAPI
import com.sun.jna.platform.WindowUtils
import com.sun.jna.platform.win32.User32
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.math.NumberUtils
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.tinylog.configuration.Configuration
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.*
import javax.swing.*
import javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import kotlin.system.exitProcess

class ApplicationRunner {
    private lateinit var singletonLock: FileLock
    private val log by lazy {
        if (!::singletonLock.isInitialized) {
            throw UnsupportedOperationException("Singleton lock is not initialized")
        }
        LoggerFactory.getLogger("Main")
    }

    fun run() {
        // 覆盖 tinylog 配置
        setupTinylog()

        // 是否单例
        checkSingleton()

        // 打印系统信息
        printSystemInfo()

        SwingUtilities.invokeAndWait {
            // 打开数据库
            openDatabase()

            // 加载设置
            loadSettings()

            // 统计
            enableAnalytics()

            // 设置 LAF
            setupLaf()

            // 解密数据
            openDoor()

            // 启动主窗口
            startMainFrame()
        }
    }


    private fun openDoor() {
        if (Doorman.instance.isWorking()) {
            if (!DoormanDialog(null).open()) {
                exitProcess(1)
            }
        }
    }

    private fun startMainFrame() {
        val frame = TermoraFrame()
        frame.title = if (SystemInfo.isLinux) null else Application.getName()
        frame.defaultCloseOperation = DISPOSE_ON_CLOSE
        frame.setSize(1280, 800)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }


    private fun loadSettings() {
        val language = Database.instance.appearance.language
        val locale = runCatching { LocaleUtils.toLocale(language) }.getOrElse { Locale.getDefault() }
        if (log.isInfoEnabled) {
            log.info("Language: {} , Locale: {}", language, locale)
        }
        Locale.setDefault(locale)
    }


    private fun setupLaf() {

        System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, "${SystemInfo.isLinux}")
        System.setProperty(FlatSystemProperties.USE_ROUNDED_POPUP_BORDER, "false")

        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true)
            JDialog.setDefaultLookAndFeelDecorated(true)
        }

        val themeManager = ThemeManager.instance
        val settings = Database.instance
        var theme = settings.appearance.theme

        // 如果是跟随系统或者不存在样式，那么使用默认的
        if (settings.appearance.followSystem || !themeManager.themes.containsKey(theme)) {
            theme = if (OsThemeDetector.getDetector().isDark) {
                "Dark"
            } else {
                "Light"
            }
        }

        themeManager.change(theme, true)

        FlatInspector.install("ctrl shift alt X");

        UIManager.put(FlatClientProperties.FULL_WINDOW_CONTENT, true)
        UIManager.put(FlatClientProperties.USE_WINDOW_DECORATIONS, false)
        UIManager.put("TitlePane.useWindowDecorations", false)

        UIManager.put("Component.arc", 5)
        UIManager.put("TextComponent.arc", UIManager.getInt("Component.arc"))
        UIManager.put("Component.hideMnemonics", false)

        UIManager.put("TitleBar.height", 36)

        UIManager.put("Dialog.width", 650)
        UIManager.put("Dialog.height", 550)


        if (SystemInfo.isMacOS) {
            UIManager.put("TabbedPane.tabHeight", UIManager.getInt("TitleBar.height"))
        } else if (SystemInfo.isLinux) {
            UIManager.put("TabbedPane.tabHeight", UIManager.getInt("TitleBar.height") - 4)
        } else {
            UIManager.put("TabbedPane.tabHeight", UIManager.getInt("TitleBar.height") - 6)
        }

        if (SystemInfo.isLinux) {
            UIManager.put("TitlePane.centerTitle", true)
            UIManager.put("TitlePane.showIcon", false)
            UIManager.put("TitlePane.showIconInDialogs", false)
        }

        UIManager.put("Table.rowHeight", 24)
        UIManager.put("Table.cellNoFocusBorder", BorderFactory.createEmptyBorder())
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder())
        UIManager.put("Table.focusSelectedCellHighlightBorder", BorderFactory.createEmptyBorder())
        UIManager.put("Table.selectionArc", UIManager.getInt("Component.arc"))

        UIManager.put("Tree.rowHeight", 24)
        UIManager.put("Tree.background", DynamicColor("window"))
        UIManager.put("Tree.selectionArc", UIManager.getInt("Component.arc"))
        UIManager.put("Tree.showCellFocusIndicator", false)
        UIManager.put("Tree.repaintWholeRow", true)

        UIManager.put("List.selectionArc", UIManager.getInt("Component.arc"))


    }

    private fun printSystemInfo() {
        if (log.isInfoEnabled) {
            log.info("Welcome to ${Application.getName()} ${Application.getVersion()}!")
            log.info(
                "JVM name: {} , vendor: {} , version: {}",
                SystemUtils.JAVA_VM_NAME,
                SystemUtils.JAVA_VM_VENDOR,
                SystemUtils.JAVA_VM_VERSION,
            )
            log.info(
                "OS name: {} , version: {} , arch: {}",
                SystemUtils.OS_NAME,
                SystemUtils.OS_VERSION,
                SystemUtils.OS_ARCH
            )
            log.info("Base config dir: ${Application.getBaseDataDir().absolutePath}")
        }
    }


    /**
     * Windows 情况覆盖
     */
    private fun setupTinylog() {
        if (SystemInfo.isWindows) {
            val dir = File(Application.getBaseDataDir(), "logs")
            FileUtils.forceMkdir(dir)
            Configuration.set("writer_file.latest", "${dir.absolutePath}/${Application.getName().lowercase()}.log")
            Configuration.set("writer_file.file", "${dir.absolutePath}/{date:yyyy}-{date:MM}-{date:dd}.log")
        }
    }


    private fun checkSingleton() {
        val file = File(Application.getBaseDataDir(), "lock")
        val pidFile = File(Application.getBaseDataDir(), "pid")


        val raf = RandomAccessFile(file, "rw")
        val lock = raf.channel.tryLock()

        if (lock != null) {
            pidFile.writeText(ProcessHandle.current().pid().toString())
            pidFile.deleteOnExit()
            file.deleteOnExit()
        } else {
            if (SystemInfo.isWindows && pidFile.exists()) {
                val pid = NumberUtils.toLong(pidFile.readText())
                for (window in WindowUtils.getAllWindows(false)) {
                    if (pid > 0) {
                        val processId = IntByReference()
                        User32.INSTANCE.GetWindowThreadProcessId(window.hwnd, processId)
                        if (processId.value.toLong() != pid) {
                            continue
                        }
                    } else if (window.title != Application.getName() || window.filePath.endsWith("explorer.exe")) {
                        continue
                    }
                    User32.INSTANCE.ShowWindow(window.hwnd, User32.SW_SHOWNOACTIVATE)
                    User32.INSTANCE.SetForegroundWindow(window.hwnd)
                    break
                }
            }

            System.err.println("Program is already running")
            exitProcess(1)
        }

        singletonLock = lock
    }


    private fun openDatabase() {
        val dir = Application.getDatabaseFile()
        try {
            Database.open(dir)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            JOptionPane.showMessageDialog(
                null, "Unable to open database",
                I18n.getString("termora.title"), JOptionPane.ERROR_MESSAGE
            )
            exitProcess(1)
        }
    }

    /**
     * 统计 https://mixpanel.com
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun enableAnalytics() {
        if (Application.isUnknownVersion()) {
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val properties = JSONObject()
                properties.put("os", SystemUtils.OS_NAME)
                if (SystemInfo.isLinux) {
                    properties.put("platform", "Linux")
                } else if (SystemInfo.isWindows) {
                    properties.put("platform", "Windows")
                } else if (SystemInfo.isMacOS) {
                    properties.put("platform", "macOS")
                }
                properties.put("version", Application.getVersion())
                properties.put("language", Locale.getDefault().toString())
                val message = MessageBuilder("0871335f59ee6d0eb246b008a20f9d1c")
                    .event(getAnalyticsUserID(), "launch", properties)
                val delivery = ClientDelivery()
                delivery.addMessage(message)
                MixpanelAPI().deliver(delivery, true)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }

    private fun getAnalyticsUserID(): String {
        var id = Database.instance.properties.getString("AnalyticsUserID")
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toSimpleString()
            Database.instance.properties.putString("AnalyticsUserID", id)
        }
        return id
    }

}