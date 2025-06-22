package app.termora.transfer

import app.termora.Disposable
import app.termora.I18n
import app.termora.NativeIcons
import app.termora.OptionPane
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXTreeTable
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.tree.DefaultTreeCellRenderer
import kotlin.io.path.name
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds


class TransferTable(private val coroutineScope: CoroutineScope, private val tableModel: TransferTableModel) :
    JXTreeTable(), Disposable {

    private val table get() = this
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val disposed = AtomicBoolean(false)

    init {
        initView()
        initEvents()
        refreshView()
    }

    private fun initView() {
        super.setTreeTableModel(tableModel)
        super.getTableHeader().setReorderingAllowed(false)
        super.setRowHeight(UIManager.getInt("Table.rowHeight"))
        super.setAutoResizeMode(AUTO_RESIZE_OFF)
        super.setFillsViewportHeight(true)
        super.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "cellMargins" to Insets(0, 4, 0, 4),
                "selectionArc" to 0,
            )
        )
        super.setTreeCellRenderer(object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree?,
                value: Any?,
                sel: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): Component {
                val node = value as DefaultMutableTreeTableNode
                val transfer = node.userObject as? Transfer
                val text = transfer?.source()?.name ?: StringUtils.EMPTY
                val c = super.getTreeCellRendererComponent(tree, text, sel, expanded, leaf, row, hasFocus)
                icon = if (transfer?.isDirectory() == true) NativeIcons.folderIcon else NativeIcons.fileIcon
                return c
            }
        })

        columnModel.getColumn(TransferTableModel.COLUMN_NAME).preferredWidth = 300
        columnModel.getColumn(TransferTableModel.COLUMN_SOURCE_PATH).preferredWidth = 200
        columnModel.getColumn(TransferTableModel.COLUMN_TARGET_PATH).preferredWidth = 200

        columnModel.getColumn(TransferTableModel.COLUMN_STATUS).preferredWidth = 100
        columnModel.getColumn(TransferTableModel.COLUMN_PROGRESS).preferredWidth = 150
        columnModel.getColumn(TransferTableModel.COLUMN_SIZE).preferredWidth = 140
        columnModel.getColumn(TransferTableModel.COLUMN_SPEED).preferredWidth = 80

        val centerTableCellRenderer = DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.CENTER }
        columnModel.getColumn(TransferTableModel.COLUMN_STATUS).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransferTableModel.COLUMN_SIZE).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransferTableModel.COLUMN_SPEED).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransferTableModel.COLUMN_ESTIMATED_TIME).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransferTableModel.COLUMN_PROGRESS).cellRenderer = ProgressTableCellRenderer()
    }

    private fun initEvents() {
        // contextmenu
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val r = table.rowAtPoint(e.point)
                    if (r >= 0 && r < table.rowCount) {
                        if (!table.isRowSelected(r)) {
                            table.setRowSelectionInterval(r, r)
                        }
                    } else {
                        table.clearSelection()
                    }

                    val rows = table.selectedRows

                    if (!table.hasFocus()) {
                        table.requestFocusInWindow()
                    }

                    showContextmenu(rows, e)
                }
            }
        })
    }

    private fun showContextmenu(rows: IntArray, e: MouseEvent) {
        val transfers = rows.map { getPathForRow(it).lastPathComponent }
            .filterIsInstance<DefaultMutableTreeTableNode>().map { it.userObject }
            .filterIsInstance<Transfer>()
        if (transfers.isEmpty()) return

        val popupMenu = FlatPopupMenu()
        val delete = popupMenu.add(I18n.getString("termora.transport.jobs.contextmenu.delete"))
        val deleteAll = popupMenu.add(I18n.getString("termora.transport.jobs.contextmenu.delete-all"))
        delete.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (OptionPane.showConfirmDialog(
                        owner,
                        I18n.getString("termora.keymgr.delete-warning"),
                        messageType = JOptionPane.WARNING_MESSAGE
                    ) != JOptionPane.YES_OPTION
                ) return
                for (transfer in transfers) {
                    tableModel.removeTransfer(transfer.id())
                }
            }
        })
        deleteAll.addActionListener {
            if (OptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    I18n.getString("termora.keymgr.delete-warning"),
                    messageType = JOptionPane.WARNING_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                tableModel.removeTransfer(StringUtils.EMPTY)
            }
        }

        delete.isEnabled = transfers.isNotEmpty()

        popupMenu.show(this, e.x, e.y)
    }

    private fun refreshView() {
        coroutineScope.launch(Dispatchers.Swing) {
            val timeout = 500
            while (coroutineScope.isActive && disposed.get().not()) {
                for (row in 0 until rowCount) {
                    val treePath = getPathForRow(row) ?: continue
                    val node = treePath.lastPathComponent as? DefaultMutableTreeTableNode ?: continue
                    tableModel.valueForPathChanged(treePath, node.userObject)
                }
                delay(timeout.milliseconds)
            }
        }
    }

    override fun dispose() {
        disposed.set(true)
    }

    private inner class ProgressTableCellRenderer : DefaultTableCellRenderer() {
        private var progress = 0.0
        private var progressInt = 0
        private val padding = 4

        init {
            horizontalAlignment = CENTER
        }

        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {

            this.progress = 0.0
            this.progressInt = 0

            if (value is TransferTreeTableNode) {
                if (value.state() == TransferTreeTableNode.State.Processing || value.waitingChildrenCompleted() || value.transfer is DeleteTransfer) {
                    this.progress = value.transferred.get() * 1.0 / value.filesize.get()
                    this.progressInt = floor(progress * 100.0).toInt()
                    // 因为有一些 0B 大小的文件，所以如果在进行中，那么最大就是99
                    if (this.progress >= 1 && value.state() == TransferTreeTableNode.State.Processing) {
                        this.progress = 0.99
                        this.progressInt = floor(progress * 100.0).toInt()
                    }
                }
            }

            return super.getTableCellRendererComponent(
                table,
                "${progressInt}%",
                isSelected,
                hasFocus,
                row,
                column
            )
        }

        override fun paintComponent(g: Graphics) {
            // 原始背景
            g.color = background
            g.fillRect(0, 0, width, height)

            // 进度条背景
            g.color = UIManager.getColor("Table.selectionInactiveBackground")
            g.fillRect(0, padding, width, height - padding * 2)

            // 进度条颜色
            g.color = UIManager.getColor("ProgressBar.foreground")
            g.fillRect(0, padding, (width * progress).toInt(), height - padding * 2)

            // 大于某个阀值的时候，就要改变颜色
            if (progress >= 0.45) {
                foreground = selectionForeground
            }

            // 绘制文字
            ui.paint(g, this)
        }
    }
}

