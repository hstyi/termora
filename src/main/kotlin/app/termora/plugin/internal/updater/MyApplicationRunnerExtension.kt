package app.termora.plugin.internal.updater

import app.termora.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import java.awt.KeyboardFocusManager
import kotlin.time.Duration.Companion.seconds

internal class MyApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        val instance = MyApplicationRunnerExtension()

        private val log = LoggerFactory.getLogger(MyApplicationRunnerExtension::class.java)
    }

    private val disabledUpdater get() = Application.getLayout() == AppLayout.Appx || Application.getLayout() == AppLayout.AppStore
    private val updaterManager get() = UpdaterManager.getInstance()


    override fun ready() {
        swingCoroutineScope.launch(Dispatchers.IO) {
            try {
                delay(3.seconds)
                scheduleUpdate()
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }


    private suspend fun scheduleUpdate() {
        if (disabledUpdater) return

        val latestVersion = updaterManager.fetchLatestVersion()
        if (latestVersion.isSelf) {
            return
        }

        val newVersion = Semver.parse(latestVersion.version) ?: return
        val version = Semver.parse(Application.getVersion()) ?: return
        if (newVersion <= version) {
            return
        }

        withContext(Dispatchers.Swing) {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
                ?: TermoraFrameManager.getInstance().getWindows().firstOrNull()
            if (owner != null) {
                val dialog = UpdaterDialog(owner, latestVersion)
                dialog.isModal = true
                dialog.isVisible = true
            }
        }

    }
}