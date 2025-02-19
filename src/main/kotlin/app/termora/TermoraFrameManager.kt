package app.termora

import com.formdev.flatlaf.util.SystemInfo
import org.slf4j.LoggerFactory
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JOptionPane
import javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE
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

    fun createWindow(): TermoraFrame {
        val frame = TermoraFrame()
        registerCloseCallback(frame)
        frame.title = if (SystemInfo.isLinux) null else Application.getName()
        frame.defaultCloseOperation = DO_NOTHING_ON_CLOSE
        frame.setSize(1280, 800)
        frame.setLocationRelativeTo(null)
        frames.add(frame)
        return frame
    }

    fun getWindows(): Array<TermoraFrame> {
        return frames.toTypedArray()
    }


    private fun registerCloseCallback(window: TermoraFrame) {
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {

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
                    if (OptionPane.showConfirmDialog(
                            window,
                            I18n.getString("termora.quit-confirm", Application.getName()),
                            optionType = JOptionPane.YES_NO_OPTION,
                        ) == JOptionPane.YES_OPTION
                    ) {
                        window.dispose()
                    }
                } else {
                    window.dispose()
                }
            }
        })
    }

    private fun dispose() {
        Disposer.dispose(ApplicationScope.forApplicationScope())

        try {
            Disposer.getTree().assertIsEmpty(true)
        } catch (e: Exception) {
            log.error(e.message)
        }

        exitProcess(0)
    }
}