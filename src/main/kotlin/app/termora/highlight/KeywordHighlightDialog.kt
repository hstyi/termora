package app.termora.highlight

import app.termora.*
import app.termora.terminal.TerminalColor
import com.formdev.flatlaf.extras.components.FlatTable
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

@Suppress("DuplicatedCode")
class KeywordHighlightDialog(owner: Window) : DialogWrapper(owner) {

    private val model = KeywordHighlightTableModel()
    private val table = FlatTable()
    private val keywordHighlightManager by lazy { KeywordHighlightManager.getInstance() }
    private val terminal by lazy { TerminalFactory.getInstance().createTerminal() }
    private val colorPalette by lazy { terminal.getTerminalModel().getColorPalette() }

    private val addBtn = JButton(I18n.getString("termora.new-host.tunneling.add"))
    private val editBtn = JButton(I18n.getString("termora.keymgr.edit"))
    private val deleteBtn = JButton(I18n.getString("termora.remove"))


    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.highlight")

        initView()
        initEvents()

        init()
        setLocationRelativeTo(null)
    }

    private fun initView() {
        model.addColumn(I18n.getString("termora.highlight.keyword"))
        model.addColumn(I18n.getString("termora.highlight.preview"))
        model.addColumn(I18n.getString("termora.highlight.description"))
        table.fillsViewportHeight = true
        table.tableHeader.reorderingAllowed = false
        table.model = model

        editBtn.isEnabled = false
        deleteBtn.isEnabled = false

        // keyword
        table.columnModel.getColumn(0).setCellRenderer(object : JCheckBox(), TableCellRenderer {
            init {
                horizontalAlignment = SwingConstants.LEFT
                verticalAlignment = SwingConstants.CENTER
            }

            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                if (value is KeywordHighlight) {
                    text = value.keyword
                    super.setSelected(value.enabled)
                }
                if (isSelected) {
                    foreground = table.selectionForeground
                    super.setBackground(table.selectionBackground)
                } else {
                    foreground = table.foreground
                    background = table.background
                }
                return this
            }

        })

        // preview
        table.columnModel.getColumn(1).setCellRenderer(object : DefaultTableCellRenderer() {
            private val keywordHighlightView = KeywordHighlightView(0)

            init {
                keywordHighlightView.border = null
            }

            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                if (value is KeywordHighlight) {
                    keywordHighlightView.setKeywordHighlight(value, colorPalette)
                    if (isSelected) keywordHighlightView.backgroundColor = table.selectionBackground
                    return keywordHighlightView
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            }
        })


    }

    private fun initEvents() {

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    val column = table.columnAtPoint(e.point)
                    if (row >= 0 && column == 0) {
                        val keywordHighlight = model.getKeywordHighlight(row)
                        keywordHighlightManager.addKeywordHighlight(keywordHighlight.copy(enabled = !keywordHighlight.enabled))
                        model.fireTableCellUpdated(row, column)
                    }
                }
            }
        })

        addBtn.addActionListener {
            val dialog = NewKeywordHighlightDialog(this, colorPalette)
            dialog.setLocationRelativeTo(this)
            dialog.isVisible = true
            val keywordHighlight = dialog.keywordHighlight
            if (keywordHighlight != null) {
                keywordHighlightManager.addKeywordHighlight(keywordHighlight)
                model.fireTableRowsInserted(model.rowCount - 1, model.rowCount)
            }
        }

        editBtn.addActionListener {
            val row = table.selectedRow
            if (row > -1) {
                var keywordHighlight = model.getKeywordHighlight(row)
                val dialog = NewKeywordHighlightDialog(this, colorPalette)
                dialog.setLocationRelativeTo(this)
                dialog.keywordTextField.text = keywordHighlight.keyword
                dialog.descriptionTextField.text = keywordHighlight.description

                if (keywordHighlight.textColor <= 16) {
                    if (keywordHighlight.textColor == 0) {
                        dialog.textColor.color = Color(colorPalette.getColor(TerminalColor.Basic.FOREGROUND))
                    } else {
                        dialog.textColor.color = Color(colorPalette.getXTerm256Color(keywordHighlight.textColor))
                    }
                    dialog.textColor.colorIndex = keywordHighlight.textColor
                } else {
                    dialog.textColor.color = Color(keywordHighlight.textColor)
                    dialog.textColor.colorIndex = -1
                }

                if (keywordHighlight.backgroundColor <= 16) {
                    if (keywordHighlight.backgroundColor == 0) {
                        dialog.backgroundColor.color = Color(colorPalette.getColor(TerminalColor.Basic.BACKGROUND))
                    } else {
                        dialog.backgroundColor.color =
                            Color(colorPalette.getXTerm256Color(keywordHighlight.backgroundColor))
                    }
                    dialog.backgroundColor.colorIndex = keywordHighlight.backgroundColor
                } else {
                    dialog.backgroundColor.color = Color(keywordHighlight.backgroundColor)
                    dialog.backgroundColor.colorIndex = -1
                }

                dialog.boldCheckBox.isSelected = keywordHighlight.bold
                dialog.italicCheckBox.isSelected = keywordHighlight.italic
                dialog.underlineCheckBox.isSelected = keywordHighlight.underline
                dialog.lineThroughCheckBox.isSelected = keywordHighlight.lineThrough
                dialog.matchCaseBtn.isSelected = keywordHighlight.matchCase
                dialog.regexBtn.isSelected = keywordHighlight.regex

                dialog.isVisible = true

                val value = dialog.keywordHighlight
                if (value != null) {
                    keywordHighlight = value.copy(id = keywordHighlight.id, sort = keywordHighlight.sort)
                    keywordHighlightManager.addKeywordHighlight(keywordHighlight)
                    model.fireTableRowsUpdated(row, row)
                }
            }

        }

        deleteBtn.addActionListener {
            if (table.selectedRowCount > 0) {
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.keymgr.delete-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    val rows = table.selectedRows.sorted().reversed()
                    for (row in rows) {
                        val id = model.getKeywordHighlight(row).id
                        keywordHighlightManager.removeKeywordHighlight(id)
                        model.fireTableRowsDeleted(row, row)
                    }
                }
            }
        }

        table.selectionModel.addListSelectionListener {
            editBtn.isEnabled = table.selectedRowCount > 0
            deleteBtn.isEnabled = editBtn.isEnabled
        }

        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                terminal.close()
            }
        })
    }

    override fun createCenterPanel(): JComponent {

        val panel = JPanel(BorderLayout())
        panel.add(JScrollPane(table).apply {
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
        }, BorderLayout.CENTER)

        var rows = 1
        val step = 2
        val formMargin = "4dlu"
        val layout = FormLayout(
            "default:grow",
            "pref, $formMargin, pref, $formMargin, pref"
        )
        panel.add(
            FormBuilder.create().layout(layout).padding(EmptyBorder(0, 12, 0, 0))
                .add(addBtn).xy(1, rows).apply { rows += step }
                .add(editBtn).xy(1, rows).apply { rows += step }
                .add(deleteBtn).xy(1, rows).apply { rows += step }
                .build(),
            BorderLayout.EAST)

        panel.border = BorderFactory.createEmptyBorder(
            if (SystemInfo.isWindows || SystemInfo.isLinux) 6 else 0,
            12, 12, 12
        )

        return panel
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }

}