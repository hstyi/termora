package app.termora

import com.formdev.flatlaf.util.SystemInfo
import org.slf4j.LoggerFactory
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE
import kotlin.math.max
import kotlin.system.exitProcess

class TermoraFrameManager {

    companion object {
        private val log = LoggerFactory.getLogger(TermoraFrameManager::class.java)

        fun getInstance(): TermoraFrameManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(TermoraFrameManager::class) { TermoraFrameManager() }
        }
    }

    private val frames = mutableListOf<TermoraFrame>()
    private val properties get() = Database.getDatabase().properties
    private val isBackgroundRunning get() = Database.getDatabase().appearance.backgroundRunning

    fun createWindow(): TermoraFrame {
        val frame = TermoraFrame().apply { registerCloseCallback(this) }
        frame.title = if (SystemInfo.isLinux) null else Application.getName()
        frame.defaultCloseOperation = DO_NOTHING_ON_CLOSE

        val rectangle = getFrameRectangle() ?: FrameRectangle(-1, -1, 1280, 800, 0)
        if (rectangle.isMaximized) {
            frame.setSize(1280, 800)
            frame.setLocationRelativeTo(null)
            frame.extendedState = rectangle.s
        } else {
            // 控制最小
            frame.setSize(
                max(rectangle.w, UIManager.getInt("Dialog.width") - 150),
                max(rectangle.h, UIManager.getInt("Dialog.height") - 100)
            )
            if (rectangle.x == -1 && rectangle.y == -1) {
                frame.setLocationRelativeTo(null)
            } else {
                frame.setLocation(max(rectangle.x, 0), max(rectangle.y, 0))
            }
        }

        return frame.apply { frames.add(this) }
    }

    fun getWindows(): Array<TermoraFrame> {
        return frames.toTypedArray()
    }


    private fun registerCloseCallback(window: TermoraFrame) {
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {

                // 存储位置信息
                saveFrameRectangle(window)

                // 删除
                frames.remove(window)

                // dispose windowScope
                val windowScope = ApplicationScope.forWindowScope(e.window)
                Disposer.disposeChildren(windowScope, null)
                Disposer.dispose(windowScope)

                val windowScopes = ApplicationScope.windowScopes()

                // 如果已经没有 Window 域了，那么就可以退出程序了
                if (windowScopes.isEmpty()) {
                    this@TermoraFrameManager.dispose()
                }
            }

            override fun windowClosing(e: WindowEvent) {
                if (ApplicationScope.windowScopes().size == 1) {
                    if (SystemInfo.isWindows && isBackgroundRunning) {
                        // 最小化
                        window.extendedState = window.extendedState or JFrame.ICONIFIED
                        // 隐藏
                        window.isVisible = false
                    } else {
                        if (OptionPane.showConfirmDialog(
                                window,
                                I18n.getString("termora.quit-confirm", Application.getName()),
                                optionType = JOptionPane.YES_NO_OPTION,
                            ) == JOptionPane.YES_OPTION
                        ) {
                            window.dispose()
                        }
                    }
                } else {
                    window.dispose()
                }
            }
        })
    }

    fun tick() {
        if (SwingUtilities.isEventDispatchThread()) {
            val windows = getWindows()
            if (windows.isEmpty()) return
            for (window in windows) {
                if (window.extendedState and JFrame.ICONIFIED == JFrame.ICONIFIED) {
                    window.extendedState = window.extendedState and JFrame.ICONIFIED.inv()
                }
                window.isVisible = true
            }
            windows.last().toFront()
        } else {
            SwingUtilities.invokeLater { tick() }
        }
    }

    private fun dispose() {
        Disposer.dispose(ApplicationScope.forApplicationScope())

        try {
            Disposer.getTree().assertIsEmpty(true)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }

        exitProcess(0)
    }

    private fun saveFrameRectangle(frame: TermoraFrame) {
        properties.putString("TermoraFrame.x", frame.x.toString())
        properties.putString("TermoraFrame.y", frame.y.toString())
        properties.putString("TermoraFrame.width", frame.width.toString())
        properties.putString("TermoraFrame.height", frame.height.toString())
        properties.putString("TermoraFrame.extendedState", frame.extendedState.toString())
    }

    private fun getFrameRectangle(): FrameRectangle? {
        val x = properties.getString("TermoraFrame.x")?.toIntOrNull() ?: return null
        val y = properties.getString("TermoraFrame.y")?.toIntOrNull() ?: return null
        val w = properties.getString("TermoraFrame.width")?.toIntOrNull() ?: return null
        val h = properties.getString("TermoraFrame.height")?.toIntOrNull() ?: return null
        val s = properties.getString("TermoraFrame.extendedState")?.toIntOrNull() ?: return null
        return FrameRectangle(x, y, w, h, s)
    }

    private data class FrameRectangle(
        val x: Int, val y: Int, val w: Int, val h: Int, val s: Int
    ) {
        val isMaximized get() = (s and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
    }
}