package app.termora.plugins.filtering

import app.termora.*
import app.termora.actions.DataProviders
import app.termora.terminal.DataKey
import app.termora.terminal.DataListener
import app.termora.terminal.Terminal
import app.termora.terminal.VisualTerminal
import app.termora.terminal.panel.vw.SSHVisualWindow
import app.termora.terminal.panel.vw.VisualWindowManager
import com.formdev.flatlaf.extras.components.FlatTextField
import com.formdev.flatlaf.ui.FlatRoundBorder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.swing.Swing
import java.awt.BorderLayout
import javax.swing.*
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

class FilteringVisualWindow(tab: PtyHostTerminalTab, visualWindowManager: VisualWindowManager) :
    SSHVisualWindow(tab, "Filtering", visualWindowManager) {

    private val rootPanel = JPanel(BorderLayout())
    private val textField = FlatTextField()
    private val channel = Channel<Unit>(Channel.CONFLATED)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val contentPanel = JPanel(BorderLayout())
    private val progressBar = JProgressBar()
    private val model = DefaultListModel<String>()
    private val list = JList(model)
    private val searchBtn = JButton(Icons.search)

    init {
        Disposer.register(tab, this)
        initViews()
        initEvents()
        initVisualWindowPanel()
    }

    private fun initViews() {
        title = "Filtering terminal"
        rootPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

//        searchBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
        searchBtn.isFocusable = false

        val box = Box.createHorizontalBox()
        box.add(textField)
        box.add(Box.createHorizontalStrut(4))
        box.add(searchBtn)
        rootPanel.add(box, BorderLayout.NORTH)

        progressBar.border = BorderFactory.createEmptyBorder(0, 0, 4, 0)

        list.background = DynamicColor("desktop")
        list.fixedCellHeight = UIManager.getInt("Tree.rowHeight")

        val panel = JPanel(BorderLayout())
        panel.add(JScrollPane(list).apply { border = BorderFactory.createEmptyBorder() }, BorderLayout.CENTER)
        panel.border = FlatRoundBorder()

        contentPanel.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
        contentPanel.add(progressBar.apply { value = 10 }, BorderLayout.NORTH)
        contentPanel.add(panel, BorderLayout.CENTER)

        rootPanel.add(contentPanel, BorderLayout.CENTER)
        add(rootPanel, BorderLayout.CENTER)
    }

    private fun initEvents() {

        val terminal = tab.getData(DataProviders.Terminal)
        val dataListener = object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                if (key != VisualTerminal.Written) return
                channel.trySend(Unit).isSuccess
            }
        }
        terminal?.getTerminalModel()?.addDataListener(dataListener)

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                terminal?.getTerminalModel()?.removeDataListener(dataListener)
                coroutineScope.cancel()
            }
        })

        coroutineScope.launch { while (isActive) refresh() }

        channel.trySend(Unit).isSuccess
    }

    private suspend fun refresh() {
        channel.receive()

        val terminal = tab.getData(DataProviders.Terminal) ?: return

        withContext(Dispatchers.Swing) { doRefresh(terminal) }


    }

    private suspend fun doRefresh(terminal: Terminal) {
        val c = textField.text
        if (c.isBlank()) return

        model.clear()
        progressBar.isVisible = true
        val lineCount = terminal.getDocument().getLineCount()

        for (i in 0 until lineCount) {
            progressBar.value = floor((i * 1.0 / lineCount) * 100.0).toInt()
            val text = terminal.getDocument().getLine(i + 1).getText()
            if (text.isBlank()) continue
            if (text.contains(c, ignoreCase = true)) {
                model.addElement("<html><font color=red>${i + 1}</font>: $text</html>")
            }
            delay(10.milliseconds)
        }

        progressBar.isVisible = false

    }

}