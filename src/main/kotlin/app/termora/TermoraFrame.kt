package app.termora


import app.termora.actions.ActionManager
import app.termora.actions.DataProvider
import app.termora.actions.DataProviderSupport
import app.termora.actions.DataProviders
import app.termora.terminal.DataKey
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.JBR
import java.awt.Dimension
import java.awt.Insets
import java.awt.KeyboardFocusManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.imageio.ImageIO
import javax.swing.Box
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.SwingUtilities.isEventDispatchThread
import javax.swing.UIManager
import kotlin.math.max

fun assertEventDispatchThread() {
    if (!isEventDispatchThread()) throw WrongThreadException("AWT EventQueue")
}


class TermoraFrame : JFrame(), DataProvider {


    private val actionManager get() = ActionManager.getInstance()
    private val id = UUID.randomUUID().toString()
    private val windowScope = ApplicationScope.forWindowScope(this)
    private val titleBar = LogicCustomTitleBar.createCustomTitleBar(this)
    private val tabbedPane = MyTabbedPane()
    private val toolbar = TermoraToolBar(titleBar, tabbedPane)
    private val terminalTabbed = TerminalTabbed(windowScope, toolbar, tabbedPane)
    private val isWindowDecorationsSupported by lazy { JBR.isWindowDecorationsSupported() }
    private val dataProviderSupport = DataProviderSupport()
    private val welcomePanel = WelcomePanel(windowScope)
    private val keyboardFocusManager by lazy { KeyboardFocusManager.getCurrentKeyboardFocusManager() }


    init {
        initView()
        initEvents()
    }

    private fun initEvents() {

        forceHitTest()

        // macos 需要判断是否全部删除
        // 当 Tab 为 0 的时候，需要加一个边距，避开控制栏
        if (SystemInfo.isMacOS && isWindowDecorationsSupported) {
            tabbedPane.addChangeListener {
                tabbedPane.leadingComponent = if (tabbedPane.tabCount == 0) {
                    Box.createHorizontalStrut(titleBar.leftInset.toInt())
                } else {
                    null
                }
            }
        }


        // 监听主题变化 需要动态修改控制栏颜色
        if (SystemInfo.isWindows && isWindowDecorationsSupported) {
            ThemeManager.getInstance().addThemeChangeListener(object : ThemeChangeListener {
                override fun onChanged() {
                    titleBar.putProperty("controls.dark", FlatLaf.isLafDark())
                }
            })
        }

    }


    private fun initView() {
        if (isWindowDecorationsSupported) {
            titleBar.height = UIManager.getInt("TabbedPane.tabHeight").toFloat()
            titleBar.putProperty("controls.dark", FlatLaf.isLafDark())
            JBR.getWindowDecorations().setCustomTitleBar(this, titleBar)
        }

        if (SystemInfo.isLinux) {
            rootPane.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true)
            rootPane.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, UIManager.getInt("TabbedPane.tabHeight"))
        }

        if (SystemInfo.isWindows || SystemInfo.isLinux) {
            val sizes = listOf(16, 20, 24, 28, 32, 48, 64)
            val loader = TermoraFrame::class.java.classLoader
            val images = sizes.mapNotNull { e ->
                loader.getResourceAsStream("icons/termora_${e}x${e}.png")?.use { ImageIO.read(it) }
            }
            iconImages = images
        }

        minimumSize = Dimension(640, 400)
        terminalTabbed.addTab(welcomePanel)

        // macOS 要避开左边的控制栏
        if (SystemInfo.isMacOS) {
            val left = max(titleBar.leftInset.toInt(), 76)
            if (tabbedPane.tabCount == 0) {
                tabbedPane.leadingComponent = Box.createHorizontalStrut(left)
            } else {
                tabbedPane.tabAreaInsets = Insets(0, left, 0, 0)
            }
        }

        Disposer.register(windowScope, terminalTabbed)
        add(terminalTabbed)

        dataProviderSupport.addData(DataProviders.TermoraFrame, this)
        dataProviderSupport.addData(DataProviders.WindowScope, windowScope)
    }


    private fun forceHitTest() {
        val mouseAdapter = object : MouseAdapter() {

            private fun hit(e: MouseEvent) {
                if (e.source == tabbedPane) {
                    val index = tabbedPane.indexAtLocation(e.x, e.y)
                    if (index >= 0) {
                        return
                    }
                }
                titleBar.forceHitTest(false)
            }

            override fun mouseClicked(e: MouseEvent) {
                hit(e)
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.source == toolbar.getJToolBar()) {
                    if (!isWindowDecorationsSupported && SwingUtilities.isLeftMouseButton(e)) {
                        if (JBR.isWindowMoveSupported()) {
                            JBR.getWindowMove().startMovingTogetherWithMouse(this@TermoraFrame, e.button)
                        }
                    }
                }
                hit(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                hit(e)
            }

            override fun mouseEntered(e: MouseEvent) {
                hit(e)
            }

            override fun mouseDragged(e: MouseEvent) {

                hit(e)
            }

            override fun mouseMoved(e: MouseEvent) {
                hit(e)
            }
        }


        terminalTabbed.addMouseListener(mouseAdapter)
        terminalTabbed.addMouseMotionListener(mouseAdapter)

        tabbedPane.addMouseListener(mouseAdapter)
        tabbedPane.addMouseMotionListener(mouseAdapter)

        toolbar.getJToolBar().addMouseListener(mouseAdapter)
        toolbar.getJToolBar().addMouseMotionListener(mouseAdapter)
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return dataProviderSupport.getData(dataKey)
            ?: terminalTabbed.getData(dataKey)
            ?: welcomePanel.getData(dataKey)
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TermoraFrame

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


}