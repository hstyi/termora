package app.termora

import com.formdev.flatlaf.util.SystemInfo
import org.slf4j.LoggerFactory
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import kotlin.system.exitProcess

class TermoraFrameManager {

    companion object {
        private val log = LoggerFactory.getLogger(TermoraFrameManager::class.java)

        fun getInstance(): TermoraFrameManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(TermoraFrameManager::class) { TermoraFrameManager() }
        }
    }

    fun createWindow(): TermoraFrame {
        val frame = TermoraFrame()
        registerCloseCallback(frame)
        frame.title = if (SystemInfo.isLinux) null else Application.getName()
        frame.defaultCloseOperation = DISPOSE_ON_CLOSE
        frame.setSize(1280, 800)
        frame.setLocationRelativeTo(null)
        return frame
    }


    private fun registerCloseCallback(window: TermoraFrame) {
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {

                // dispose windowScope
                Disposer.dispose(ApplicationScope.forWindowScope(e.window))

                val windowScopes = ApplicationScope.windowScopes()

                // 如果已经没有 Window 域了，那么就可以退出程序了
                if (windowScopes.isEmpty()) {
                    this@TermoraFrameManager.dispose()
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