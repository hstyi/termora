package app.termora

import app.termora.terminal.PtyConnector
import app.termora.terminal.Terminal
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

class PtyConnectorReader(
    private val ptyConnector: PtyConnector,
    private val terminal: Terminal,
) {

    companion object {
        private val log = LoggerFactory.getLogger(PtyConnectorReader::class.java)
    }

    suspend fun start() {
        var i: Int
        val buffer = CharArray(1024 * 8)

        while ((ptyConnector.read(buffer).also { i = it }) != -1) {
            if (i == 0) {
                delay(10.milliseconds)
                continue
            }
            val text = String(buffer, 0, i)
            SwingUtilities.invokeLater { terminal.write(text) }
        }

        if (log.isDebugEnabled) {
            log.debug("PtyConnectorReader stopped")
        }
    }

}