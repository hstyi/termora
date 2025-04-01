package app.termora.terminal.panel.vw

import app.termora.Disposable
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

abstract class AutoRefreshPanel : JPanel(), Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(AutoRefreshPanel::class.java)
    }

    protected val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected abstract suspend fun refresh(isFirst: Boolean)

    init {
        coroutineScope.launch {
            var isFirst = true
            while (coroutineScope.isActive) {
                try {
                    refresh(isFirst)
                    isFirst = false
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                    if (isFirst) {
                        break
                    }
                } finally {
                    delay(1000.milliseconds)
                }
            }
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
    }
}