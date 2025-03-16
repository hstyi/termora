package app.termora

import app.termora.actions.ActionManager
import app.termora.keymap.KeymapManager
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatSystemProperties
import com.formdev.flatlaf.extras.FlatDesktop
import com.formdev.flatlaf.extras.FlatInspector
import com.formdev.flatlaf.ui.FlatTableCellBorder
import com.formdev.flatlaf.util.SystemInfo
import com.jthemedetecor.OsThemeDetector
import com.mixpanel.mixpanelapi.ClientDelivery
import com.mixpanel.mixpanelapi.MessageBuilder
import com.mixpanel.mixpanelapi.MixpanelAPI
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.LocaleUtils
import org.apache.commons.lang3.SystemUtils
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*
import javax.swing.*
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

class ApplicationRunner {
    private val log by lazy { LoggerFactory.getLogger(ApplicationRunner::class.java) }

    fun run() {
        measureTimeMillis {

            // 打印系统信息
            val printSystemInfo = measureTimeMillis { printSystemInfo() }

            // 打开数据库
            val openDatabase = measureTimeMillis { openDatabase() }

            // 加载设置
            val loadSettings = measureTimeMillis { loadSettings() }

            // 统计
            val enableAnalytics = measureTimeMillis { enableAnalytics() }

            // init ActionManager、KeymapManager
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                ActionManager.getInstance()
                KeymapManager.getInstance()
            }

            // 设置 LAF
            val setupLaf = measureTimeMillis { setupLaf() }

            // 解密数据
            val openDoor = measureTimeMillis { openDoor() }

            // clear temporary
            clearTemporary()

            // 启动主窗口
            val startMainFrame = measureTimeMillis { startMainFrame() }

            if (log.isDebugEnabled) {
                log.debug("printSystemInfo: {}ms", printSystemInfo)
                log.debug("openDatabase: {}ms", openDatabase)
                log.debug("loadSettings: {}ms", loadSettings)
                log.debug("enableAnalytics: {}ms", enableAnalytics)
                log.debug("setupLaf: {}ms", setupLaf)
                log.debug("openDoor: {}ms", openDoor)
                log.debug("startMainFrame: {}ms", startMainFrame)
            }
        }.let {
            if (log.isDebugEnabled) {
                log.debug("run: {}ms", it)
            }
        }
    }

    @Suppress("OPT_IN_USAGE")
    private fun clearTemporary() {
        GlobalScope.launch(Dispatchers.IO) {
            // 启动时清除
            FileUtils.cleanDirectory(Application.getTemporaryDir())
        }

    }

    private fun openDoor() {
        if (Doorman.getInstance().isWorking()) {
            if (!DoormanDialog(null).open()) {
                exitProcess(1)
            }
        }
    }

    private fun startMainFrame() {


        TermoraFrameManager.getInstance().createWindow().isVisible = true

        if (SystemUtils.IS_OS_MAC_OSX) {
            SwingUtilities.invokeLater { FlatDesktop.setQuitHandler { quitHandler() } }
        }
    }

    private fun quitHandler() {
        for (frame in TermoraFrameManager.getInstance().getWindows()) {
            frame.dispose()
        }
    }

    private fun loadSettings() {
        val language = Database.getDatabase().appearance.language
        val locale = runCatching { LocaleUtils.toLocale(language) }.getOrElse { Locale.getDefault() }
        if (log.isInfoEnabled) {
            log.info("Language: {} , Locale: {}", language, locale)
        }
        Locale.setDefault(locale)
    }


    private fun setupLaf() {

        System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, "${SystemInfo.isLinux || SystemInfo.isWindows}")
        System.setProperty(FlatSystemProperties.USE_ROUNDED_POPUP_BORDER, "false")

        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true)
            JDialog.setDefaultLookAndFeelDecorated(true)
        }

        val themeManager = ThemeManager.getInstance()
        val appearance = Database.getDatabase().appearance
        var theme = appearance.theme
        // 如果是跟随系统
        if (appearance.followSystem) {
            theme = if (OsThemeDetector.getDetector().isDark) {
                appearance.darkTheme
            } else {
                appearance.lightTheme
            }
        }

        themeManager.change(theme, true)


//        if (Application.isUnknownVersion())
        FlatInspector.install("ctrl X")

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
        UIManager.put("Table.focusCellHighlightBorder", FlatTableCellBorder.Default())
        UIManager.put("Table.focusSelectedCellHighlightBorder", FlatTableCellBorder.Default())
        UIManager.put("Table.selectionArc", UIManager.getInt("Component.arc"))

        UIManager.put("Tree.rowHeight", 24)
        UIManager.put("Tree.background", DynamicColor("window"))
        UIManager.put("Tree.selectionArc", UIManager.getInt("Component.arc"))
        UIManager.put("Tree.showCellFocusIndicator", false)
        UIManager.put("Tree.repaintWholeRow", true)

        UIManager.put("List.selectionArc", UIManager.getInt("Component.arc"))


    }

    private fun printSystemInfo() {
        if (log.isDebugEnabled) {
            log.debug("Welcome to ${Application.getName()} ${Application.getVersion()}!")
            log.debug(
                "JVM name: {} , vendor: {} , version: {}",
                SystemUtils.JAVA_VM_NAME,
                SystemUtils.JAVA_VM_VENDOR,
                SystemUtils.JAVA_VM_VERSION,
            )
            log.debug(
                "OS name: {} , version: {} , arch: {}",
                SystemUtils.OS_NAME,
                SystemUtils.OS_VERSION,
                SystemUtils.OS_ARCH
            )
            log.debug("Base config dir: ${Application.getBaseDataDir().absolutePath}")
        }
    }


    private fun openDatabase() {
        try {
            Database.getDatabase()
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
        var id = Database.getDatabase().properties.getString("AnalyticsUserID")
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toSimpleString()
            Database.getDatabase().properties.putString("AnalyticsUserID", id)
        }
        return id
    }

}