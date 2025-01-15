package app.termora.macro

import app.termora.*
import app.termora.AES.encodeBase64String
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import org.apache.commons.lang3.time.DateFormatUtils
import org.slf4j.LoggerFactory
import java.util.*
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.min

class MacroAction : AnAction(I18n.getString("termora.macro"), Icons.rec) {

    companion object {
        private val log = LoggerFactory.getLogger(MacroAction::class.java)
    }

    init {
        setStateAction()
    }

    var isRecording = false
        private set

    private val macroManager get() = MacroManager.getInstance()


    override fun actionPerformed(evt: AnActionEvent) {
        val source = evt.source
        if (source !is JComponent) return
        val windowScope = evt.getData(DataProviders.WindowScope) ?: return

        isSelected = isRecording

        val menu = FlatPopupMenu()
        val startRecord = menu.add(MacroStartRecordingAction(Icons.empty))
        startRecord.isEnabled = !isRecording

        val stopRecord = menu.add(MacroStopRecordingAction(Icons.empty))
        stopRecord.isEnabled = isRecording

        val macros = macroManager.getMacros().sortedByDescending { it.sort }


        // 播放最后一个
        menu.add(MacroPlaybackAction())

        if (macros.isNotEmpty()) {
            menu.addSeparator()
            val count = min(macros.size, 10)
            for (i in 0 until count) {
                val macro = macros[i]
                menu.add(macro.name).addActionListener { runMacro(windowScope, macro) }
            }
        }

        menu.addSeparator()
        menu.add(I18n.getString("termora.macro.manager")).addActionListener {
            MacroDialog(SwingUtilities.getWindowAncestor(source))
                .isVisible = true
        }

        val width = menu.preferredSize.width
        menu.show(source, -(width / 2) + source.width / 2, source.height)

    }

    override fun isSelected(): Boolean {
        return isRecording
    }

    fun startRecording() {
        isSelected = true
        isRecording = true
        smallIcon = Icons.stop
    }

    fun stopRecording() {
        isSelected = false
        isRecording = false
        smallIcon = Icons.rec

        val bytes = MacroPtyConnector.getRecodingByteArray()
        if (bytes.isEmpty()) return

        val macro = Macro(
            macro = bytes.encodeBase64String(),
            name = DateFormatUtils.format(Date(), I18n.getString("termora.date-format"))
        )
        macroManager.addMacro(macro)
    }

    fun runMacro(windowScope: WindowScope, macro: Macro) {
        val terminalTabbedManager = windowScope.get(TerminalTabbedManager::class)

        val tab = terminalTabbedManager.getSelectedTerminalTab() ?: return
        if (tab !is PtyHostTerminalTab) {
            return
        }

        // 修改排序
        macroManager.addMacro(macro.copy(sort = System.currentTimeMillis()))

        try {
            tab.getPtyConnector().write(macro.macroByteArray)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error("Unable to write macro ${macro.name}", e)
            }
        }
    }
}