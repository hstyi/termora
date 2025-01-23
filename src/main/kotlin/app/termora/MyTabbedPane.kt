package app.termora

import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.StringUtils
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.util.*
import javax.swing.ImageIcon
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.math.abs

class MyTabbedPane : FlatTabbedPane() {

    private val owner: Window get() = SwingUtilities.getWindowAncestor(this)
    private val dragMouseAdaptor = DragMouseAdaptor()
    private val terminalTabbedManager
        get() = AnActionEvent(this, StringUtils.EMPTY, EventObject(this))
            .getData(DataProviders.TerminalTabbedManager)

    init {
        initEvents()
    }


    private fun initEvents() {
        addMouseListener(dragMouseAdaptor)
        addMouseMotionListener(dragMouseAdaptor)
    }

    override fun setSelectedIndex(index: Int) {
        val oldIndex = selectedIndex
        super.setSelectedIndex(index)
        firePropertyChange("selectedIndex", oldIndex, index)
    }


    private inner class DragMouseAdaptor : MouseAdapter(), KeyEventDispatcher {
        private var mousePressedPoint = Point()
        private var tabIndex = 0 - 1
        private var cancelled = false
        private var window: Window? = null
        private var terminalTab: TerminalTab? = null
        private var isDragging = false
        private var lastVisitTabIndex = -1

        override fun mousePressed(e: MouseEvent) {
            val index = indexAtLocation(e.x, e.y)
            if (index < 0 || !isTabClosable(index)) {
                return
            }
            tabIndex = index
            mousePressedPoint = e.point
        }

        override fun mouseDragged(e: MouseEvent) {
            // 如果正在拖拽中，那么修改 Window 的位置
            if (isDragging) {
                window?.location = e.locationOnScreen
                lastVisitTabIndex = indexAtLocation(e.x, e.y)
            } else if (tabIndex >= 0) { // 这里之所以判断是确保在 mousePressed 时已经确定了 Tab
                // 有的时候会太灵敏，这里容错一下
                val diff = 5
                if (abs(mousePressedPoint.y - e.y) >= diff || abs(mousePressedPoint.x - e.x) >= diff) {
                    startDrag(e)
                }
            }
        }

        private fun startDrag(e: MouseEvent) {
            if (isDragging) return
            val terminalTabbedManager = terminalTabbedManager ?: return
            val window = JDialog(owner).also { this.window = it }
            window.isUndecorated = true
            val image = createTabImage(tabIndex)
            window.size = Dimension(image.width, image.height)
            window.add(JLabel(ImageIcon(image)))
            window.location = e.locationOnScreen
            window.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .removeKeyEventDispatcher(this@DragMouseAdaptor)
                }

                override fun windowOpened(e: WindowEvent) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .addKeyEventDispatcher(this@DragMouseAdaptor)
                }
            })

            // 暂时关闭 Tab
            terminalTabbedManager.closeTerminalTab(terminalTabbedManager.getTerminalTabs()[tabIndex].also {
                terminalTab = it
            }, false)

            window.isVisible = true

            isDragging = true
            cancelled = false
        }

        private fun stopDrag() {
            if (!isDragging) {
                return
            }

            val tab = this.terminalTab
            val terminalTabbedManager = terminalTabbedManager

            if (tab != null && terminalTabbedManager != null) {
                // 如果是手动取消
                if (cancelled) {
                    terminalTabbedManager.addTerminalTab(tabIndex, tab)
                } else if (lastVisitTabIndex > 0) {
                    terminalTabbedManager.addTerminalTab(lastVisitTabIndex, tab)
                } else if (lastVisitTabIndex == 0) {
                    terminalTabbedManager.addTerminalTab(1, tab)
                } else {
                    terminalTabbedManager.addTerminalTab(tab)
                }
            }

            // reset
            window?.dispose()
            isDragging = false
            tabIndex = -1
            cancelled = false
            lastVisitTabIndex = -1
        }

        override fun mouseReleased(e: MouseEvent) {
            stopDrag()
        }

        private fun createTabImage(index: Int): BufferedImage {
            val tabBounds = getBoundsAt(index)
            val image = BufferedImage(tabBounds.width, tabBounds.height, BufferedImage.TYPE_INT_ARGB)
            val g2 = image.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.translate(-tabBounds.x, -tabBounds.y)
            paint(g2)
            g2.dispose()
            return image
        }

        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (e.keyCode == KeyEvent.VK_ESCAPE) {
                cancelled = true
                stopDrag()
                return true
            }
            return false
        }
    }


}