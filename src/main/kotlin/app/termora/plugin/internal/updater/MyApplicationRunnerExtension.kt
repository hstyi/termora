package app.termora.plugin.internal.updater

import app.termora.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import java.awt.KeyboardFocusManager
import kotlin.time.Duration.Companion.seconds

internal class MyApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        val instance = MyApplicationRunnerExtension()

        private val log = LoggerFactory.getLogger(MyApplicationRunnerExtension::class.java)
    }

    private val disabledUpdater get() = Application.getLayout() == AppLayout.Appx
    private val updaterManager get() = UpdaterManager.getInstance()


    override fun ready() {
        swingCoroutineScope.launch {
            try {
                delay(3.seconds)
                scheduleUpdate()
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }


    private fun scheduleUpdate() {
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

        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
            ?: TermoraFrameManager.getInstance().getWindows().firstOrNull()
        if (owner == null) return

        val dialog = UpdaterDialog(owner, latestVersion)
        dialog.isModal = true
        dialog.isVisible = true

    }
}