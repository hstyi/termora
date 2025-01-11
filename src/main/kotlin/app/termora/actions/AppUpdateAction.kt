package app.termora.actions

import app.termora.*
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXEditorPane
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.UIManager
import javax.swing.event.HyperlinkEvent
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AppUpdateAction : AnAction(
    StringUtils.EMPTY,
    Icons.ideUpdate
) {

    private val updaterManager get() = UpdaterManager.getInstance()

    init {
        isEnabled = false
        scheduleUpdate()
    }

    override fun actionPerformed(evt: AnActionEvent) {
        showUpdateDialog()
    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun scheduleUpdate() {
        fixedRateTimer(
            name = "check-update-timer",
            initialDelay = 3.minutes.inWholeMilliseconds,
            period = 5.hours.inWholeMilliseconds, daemon = true
        ) {
            GlobalScope.launch(Dispatchers.IO) { supervisorScope { launch { checkUpdate() } } }
        }
    }

    private suspend fun checkUpdate() {

        val latestVersion = updaterManager.fetchLatestVersion()
        if (latestVersion.isSelf) {
            return
        }

        val newVersion = Version(latestVersion.version)
        val version = Version(Application.getVersion())
        if (newVersion <= version) {
            return
        }

        if (updaterManager.isIgnored(latestVersion.version)) {
            return
        }

        withContext(Dispatchers.Swing) {
            ActionManager.getInstance()
                .setEnabled(Actions.APP_UPDATE, true)
        }

    }

    private fun showUpdateDialog() {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        val lastVersion = updaterManager.lastVersion
        val editorPane = JXEditorPane()
        editorPane.contentType = "text/html"
        editorPane.text = lastVersion.htmlBody
        editorPane.isEditable = false
        editorPane.addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                Application.browse(it.url.toURI())
            }
        }
        editorPane.background = DynamicColor("window")
        val scrollPane = JScrollPane(editorPane)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.preferredSize = Dimension(
            UIManager.getInt("Dialog.width") - 100,
            UIManager.getInt("Dialog.height") - 100
        )

        val option = OptionPane.showConfirmDialog(
            owner,
            scrollPane,
            title = I18n.getString("termora.update.title"),
            messageType = JOptionPane.PLAIN_MESSAGE,
            optionType = JOptionPane.YES_NO_CANCEL_OPTION,
            options = arrayOf(
                I18n.getString("termora.update.update"),
                I18n.getString("termora.update.ignore"),
                I18n.getString("termora.cancel")
            ),
            initialValue = I18n.getString("termora.update.update")
        )
        if (option == JOptionPane.CANCEL_OPTION) {
            return
        } else if (option == JOptionPane.NO_OPTION) {
            ActionManager.getInstance().setEnabled(Actions.APP_UPDATE, false)
            updaterManager.ignore(updaterManager.lastVersion.version)
        } else if (option == JOptionPane.YES_OPTION) {
            ActionManager.getInstance()
                .setEnabled(Actions.APP_UPDATE, false)
            Application.browse(URI.create("https://github.com/TermoraDev/termora/releases/tag/${lastVersion.version}"))
        }
    }
}