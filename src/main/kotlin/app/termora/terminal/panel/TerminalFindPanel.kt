package app.termora.terminal.panel

import app.termora.DynamicColor
import app.termora.Icons
import app.termora.terminal.*
import com.formdev.flatlaf.extras.components.FlatTextField
import com.formdev.flatlaf.extras.components.FlatToolBar
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 搜索面板
 */
class TerminalFindPanel(
    private val terminalPanel: TerminalPanel,
    private val terminal: Terminal,
) : JPanel(BorderLayout()) {


    private val markupModel get() = terminal.getMarkupModel()

    var kinds: List<FindKind> = emptyList()
        private set
    val textField = FlatTextField()

    private var index = -1
    private val prev = JButton(Icons.up)
    private val next = JButton(Icons.down)
    private val label = JLabel()
    private val matchCase = JToggleButton(Icons.matchCase, false)

    init {
        init()
    }


    private fun init() {


        textField.isShowClearButton = true
        textField.focusTraversalKeysEnabled = false
        textField.border = BorderFactory.createEmptyBorder()
        textField.padding = Insets(0, 4, 0, 0)
        textField.background = DynamicColor("window")

        val box = FlatToolBar()
        box.add(label)
        box.add(Box.createHorizontalStrut(4))
        box.add(matchCase)
        box.add(Box.createHorizontalStrut(2))
        textField.trailingComponent = box

        prev.addActionListener { next(false) }
        next.addActionListener { next(true) }
        label.isEnabled = false

        matchCase.addActionListener {
            search()
        }

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                changedUpdate(e)
            }

            override fun removeUpdate(e: DocumentEvent) {
                changedUpdate(e)
            }

            override fun changedUpdate(e: DocumentEvent) {
                search()
            }
        })

        val toolBar = FlatToolBar()
        toolBar.add(textField)
        toolBar.add(prev)
        toolBar.add(next)
        toolBar.add(JButton(Icons.close).apply {
            addActionListener {
                terminalPanel.hideFind()
            }
        })
        add(toolBar, BorderLayout.CENTER)

        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    terminalPanel.hideFind()
                } else if (e.keyCode == KeyEvent.VK_ENTER) {
                    next(true)
                }
            }
        })


        border = BorderFactory.createMatteBorder(0, 1, 1, 1, DynamicColor.BorderColor)

        val dataListener = object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                if (key == TerminalModel.Resize) {
                    search()
                }
            }
        }

        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) {
                SwingUtilities.invokeLater { textField.requestFocusInWindow() }
                terminal.getTerminalModel().addDataListener(dataListener)
            }

            override fun componentHidden(e: ComponentEvent) {
                textField.text = ""
                search()
                terminal.getTerminalModel().removeDataListener(dataListener)
            }
        })
    }

    private fun search() {
        textField.outline = null
        label.text = ""
        index = -1

        // 删除之前的结果
        markupModel.removeAllHighlighters(Highlighter.FIND)
        terminal.getSelectionModel().clearSelection()

        kinds = terminal.getFindModel().find(textField.text, !matchCase.isSelected)
        for (kind in kinds) {
            markupModel.addHighlighter(
                FindHighlighter(
                    HighlighterRange(kind.startPosition, kind.endPosition),
                    terminal
                )
            )
        }

        if (kinds.isEmpty()) {
            if (textField.text.isNotEmpty()) {
                textField.outline = "error"
            }
        } else {
            label.text = "1/${kinds.size}"
        }

        next.isEnabled = kinds.isNotEmpty()
        prev.isEnabled = kinds.isNotEmpty()
        terminalPanel.repaintImmediate()

    }


    private fun next(next: Boolean) {
        if (kinds.isEmpty()) {
            return
        }

        if (next) {
            if (index + 1 >= kinds.size) {
                index = 0
            } else {
                index++
            }
        } else {
            if (index - 1 <= 0) {
                index = kinds.size - 1
            } else {
                index--
            }
        }


        val kind = kinds[index]
        terminal.getScrollingModel().scrollToRow(kind.startPosition.y)
        terminal.getSelectionModel().setSelection(kind.startPosition, kind.endPosition)
        label.text = "${index + 1}/${kinds.size}"
    }


}