package app.termora.actions

import app.termora.I18n
import app.termora.Icons
import app.termora.SettingsDialog
import com.formdev.flatlaf.extras.FlatDesktop
import org.apache.commons.lang3.StringUtils
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

class SettingsAction : AnAction(
    I18n.getString("termora.setting"),
    Icons.settings
) {
    companion object {

        /**
         * 打开设置
         */
        const val SETTING = "SettingAction"
    }

    private var isShowing = false

    init {
        FlatDesktop.setPreferencesHandler {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (owner != null) {
                actionPerformed(ActionEvent(owner, ActionEvent.ACTION_PERFORMED, StringUtils.EMPTY))
            }
        }
    }

    override fun actionPerformed(evt: AnActionEvent) {
        if (isShowing) {
            return
        }

        isShowing = true

        val owner = evt.window
        val dialog = SettingsDialog(owner)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                this@SettingsAction.isShowing = false
            }
        })
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
    }
}