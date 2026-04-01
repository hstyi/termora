package app.termora.terminal.panel.vw

import app.termora.*
import app.termora.actions.DataProviders
import app.termora.database.DatabaseManager
import app.termora.plugin.internal.local.LocalTerminalTab
import app.termora.plugin.internal.ssh.SSHTerminalTab
import app.termora.plugin.internal.ssh.SshClients
import app.termora.terminal.panel.TerminalWriter
import com.formdev.flatlaf.extras.components.FlatTextField
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.*
import javax.swing.*


internal open class CommandHistoryVisualWindow(
    private val tab: HostTerminalTab,
    visualWindowManager: VisualWindowManager
) : VisualWindowPanel("CommandHistory", visualWindowManager), Resumeable {

    companion object {
        private val log = LoggerFactory.getLogger(CommandHistoryVisualWindow::class.java)
    }

    private val commandHistoryPanel by lazy { SystemInformationPanel() }
    private val database get() = DatabaseManager.getInstance()

    init {
        Disposer.register(tab, this)
        initViews()
        initEvents()
        initVisualWindowPanel()
    }


    private fun initViews() {
        title = I18n.getString("termora.visual-window.command-history")
        add(commandHistoryPanel, BorderLayout.CENTER)
    }

    private fun initEvents() {
        Disposer.register(this, commandHistoryPanel)
    }

    private inner class SystemInformationPanel : AutoRefreshPanel() {

        private val zshHistoryFile = File(SystemUtils.getUserHome(), ".zsh_history")
        private val bashHistoryFile = File(SystemUtils.getUserHome(), ".bash_history")
        private val model = DefaultListModel<String>()
        private val filterTextField = FlatTextField()
        private val historyList = object : JList<String>(model) {
            override fun getScrollableTracksViewportWidth(): Boolean {
                return true
            }

            override fun getToolTipText(event: MouseEvent): String? {
                val index = locationToIndex(event.point)
                if (index >= 0) {
                    val bounds = getCellBounds(index, index)
                    if (Objects.nonNull(bounds)) {
                        if (bounds.contains(event.point)) {
                            return model.getElementAt(index)
                        }
                    }
                }
                return super.getToolTipText(event)
            }
        }

        init {
            initViews()
            initEvents()
        }


        private fun initViews() {
            layout = BorderLayout()
            add(createPanel(), BorderLayout.CENTER)

            historyList.fixedCellHeight = UIManager.getInt("Tree.rowHeight")
            historyList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            historyList.background = DynamicColor("window")
            historyList.cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val text = value?.toString() ?: StringUtils.EMPTY
                    return super.getListCellRendererComponent(
                        list,
                        text,
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
            }

            filterTextField.background = historyList.background
            filterTextField.leadingIcon = Icons.find
            filterTextField.isShowClearButton = true
        }

        private fun createPanel(): JComponent {
            val formMargin = "2dlu"
            var rows = 1
            val step = 2

            val scrollPane = JScrollPane(historyList)
            scrollPane.border = BorderFactory.createEmptyBorder()
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)

            return FormBuilder.create().debug(false)
                .layout(FormLayout("default:grow", "fill:default:grow"))
//                .add(filterTextField).xy(1, rows).apply { rows += step }
                .add(scrollPane).xy(1, rows)
                .padding("$formMargin, $formMargin, $formMargin, $formMargin")
                .build()
        }

        private fun initEvents() {
            historyList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount % 2 == 0) {
                        val selectedIndex = historyList.selectedIndex
                        if (selectedIndex < 0) return
                        val text = model.getElementAt(selectedIndex)
                        if (text.isBlank()) return
                        val writer = tab.getData(DataProviders.TerminalWriter) ?: return
                        writer.write(TerminalWriter.WriteRequest.fromBytes(text.toByteArray(writer.getCharset())))
                        SwingUtilities.invokeLater { tab.getData(DataProviders.TerminalPanel)?.requestFocusInWindow() }
                    }
                }
            })
        }

        override suspend fun refresh(isFirst: Boolean) {
            val lines = mutableListOf<String>()

            if (tab is LocalTerminalTab) {
                val localShell = database.terminal.localShell
                if (localShell.isBlank()) return
                if (localShell.contains("zsh") && zshHistoryFile.exists()) {
                    val text = String(ZshHistoryCodec.unmetafy(readHistoryFile(zshHistoryFile)))
                    lines.addAll(ZshParser.parse(text.lines().iterator()))
                } else if (bashHistoryFile.exists()) {
                    val text = String(readHistoryFile(bashHistoryFile))
                    lines.addAll(BashParser.parse(text.lines().iterator()))
                }
            } else if (tab is SSHTerminalTab) {
                val session = tab.getData(SSHTerminalTab.SSHSession)
                if (session != null) {
                    val pair = SshClients.execChannel(session, "tail -n 100 ~/.bash_history")
                    if (pair.first == 0) {
                        lines.addAll(BashParser.parse(pair.second.lines().iterator()))
                    }
                }
            }

            if (lines.isEmpty()) return

            if (model.isEmpty) {
                withContext(Dispatchers.Swing) { model.addAll(lines) }
                return
            }

            withContext(Dispatchers.Swing) {
                val added = mutableListOf<String>()
                while (lines.isNotEmpty()) {
                    if (model.firstElement() != lines.first()) {
                        added.add(lines.removeFirst())
                    } else {
                        break
                    }
                }

                if (added.isNotEmpty()) {
                    val selectedIndex = historyList.selectedIndex
                    for ((i, element) in added.withIndex()) {
                        model.insertElementAt(element, i)
                    }
                    if (selectedIndex >= 0) {
                        historyList.selectedIndex = selectedIndex + added.size
                    }
                }
            }
        }

        private fun readHistoryFile(file: File): ByteArray {
            val process = Runtime.getRuntime().exec(arrayOf("tail", "-n", "100", file.absolutePath))
            if (process.waitFor() == 0) {
                return process.inputStream.use { it.readAllBytes() }
            }
            return byteArrayOf()
        }


    }

    protected object Parser {
        fun parse(iterator: Iterator<String>, lineCallback: (String) -> String): List<String> {
            val lines = mutableListOf<String>()
            val sb = StringBuilder()
            while (iterator.hasNext()) {
                var line = iterator.next()
                if (line.isBlank()) continue
                sb.clear().append(lineCallback.invoke(line))
                if (line.endsWith('\\')) {
                    while (line.endsWith('\\') && iterator.hasNext()) {
                        line = iterator.next()
                        sb.appendLine().append(line)
                    }
                }
                lines.addLast(sb.toString())
            }
            return lines
        }
    }

    protected object ZshParser {
        fun parse(iterator: Iterator<String>): List<String> {
            return Parser.parse(iterator) { it.split(";".toRegex(), 2).last() }.reversed()
        }
    }

    protected object BashParser {
        fun parse(iterator: Iterator<String>): List<String> {
            return Parser.parse(iterator) { it }.reversed()
        }
    }

    // https://www.zsh.org/mla/users/2011/msg00154.html
    protected object ZshHistoryCodec {
        private const val META = 0x83
        private const val META_MASK = 0x20

        fun unmetafy(input: ByteArray): ByteArray {
            val out = ByteArray(input.size)
            var i = 0
            var j = 0

            while (i < input.size) {
                val b = input[i].toInt() and 0xFF
                if (b == META) {
                    if (i + 1 >= input.size) {
                        out[j++] = input[i]
                        i++
                    } else {
                        val next = input[i + 1].toInt() and 0xFF
                        out[j++] = (next xor META_MASK).toByte()
                        i += 2
                    }
                } else {
                    out[j++] = input[i]
                    i++
                }
            }

            return out.copyOf(j)
        }
    }


}
