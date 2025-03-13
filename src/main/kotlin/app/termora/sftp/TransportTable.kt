package app.termora.sftp

import app.termora.Disposable
import app.termora.Disposer
import app.termora.I18n
import app.termora.OptionPane
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.jdesktop.swingx.JXTreeTable
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.tree.DefaultTreeCellRenderer
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

@Suppress("DuplicatedCode")
class TransportTable : JXTreeTable(), Disposable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val model = TransportTableModel(coroutineScope)


    private val table = this
    private val transportManager = model as TransportManager

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.getTableHeader().setReorderingAllowed(false)
        super.setTreeTableModel(model)
        super.setRowHeight(UIManager.getInt("Table.rowHeight"))
        super.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
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
                val transport = node.userObject as? Transport
                val text = Objects.toString(node.getValueAt(TransportTableModel.COLUMN_NAME))
                val c = super.getTreeCellRendererComponent(tree, text, sel, expanded, leaf, row, hasFocus)
                icon = if (transport?.isDirectory == true) NativeFileIcons.getFolderIcon()
                else NativeFileIcons.getFileIcon(text)
                return c
            }
        })

        columnModel.getColumn(TransportTableModel.COLUMN_NAME).preferredWidth = 300
        columnModel.getColumn(TransportTableModel.COLUMN_SOURCE_PATH).preferredWidth = 200
        columnModel.getColumn(TransportTableModel.COLUMN_TARGET_PATH).preferredWidth = 200

        columnModel.getColumn(TransportTableModel.COLUMN_STATUS).preferredWidth = 100
        columnModel.getColumn(TransportTableModel.COLUMN_PROGRESS).preferredWidth = 150
        columnModel.getColumn(TransportTableModel.COLUMN_SIZE).preferredWidth = 140
        columnModel.getColumn(TransportTableModel.COLUMN_SPEED).preferredWidth = 80

        val centerTableCellRenderer = DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.CENTER }
        columnModel.getColumn(TransportTableModel.COLUMN_STATUS).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransportTableModel.COLUMN_SIZE).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransportTableModel.COLUMN_SPEED).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransportTableModel.COLUMN_ESTIMATED_TIME).cellRenderer = centerTableCellRenderer
        columnModel.getColumn(TransportTableModel.COLUMN_PROGRESS).cellRenderer =
            object : DefaultTableCellRenderer() {
                private var progress = 0.0
                private var progressInt = 0
                private val padding = 4

                init {
                    horizontalAlignment = SwingConstants.CENTER
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

                    if (value is Transport) {
                        if (value.status == TransportStatus.Processing) {
                            this.progress = value.transferredFilesize.get() * 1.0 / value.filesize.get()
                            this.progressInt = floor(progress * 100.0).toInt()
                            // 因为有一些 0B 大小的文件，所以如果在进行中，那么最大就是99
                            if (this.progress >= 1 && value.status == TransportStatus.Processing) {
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


    private fun initEvents() {
        // contextmenu
        table.addMouseListener(object : MouseAdapter() {
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

                    showContextMenu(rows, e)
                }
            }
        })

        // 刷新状态
        coroutineScope.launch(Dispatchers.Swing) { refreshView() }


        // Delete key
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if ((SystemInfo.isMacOS && e.keyCode == KeyEvent.VK_BACK_SPACE) || (e.keyCode == KeyEvent.VK_DELETE)) {
                    val transports = selectedRows.map { getPathForRow(it).lastPathComponent }
                        .filterIsInstance<TransportTreeTableNode>().map { it.transport }
                    if (transports.isEmpty()) return
                    if (OptionPane.showConfirmDialog(
                            SwingUtilities.getWindowAncestor(table),
                            I18n.getString("termora.keymgr.delete-warning"),
                            messageType = JOptionPane.WARNING_MESSAGE
                        ) == JOptionPane.YES_OPTION
                    ) {
                        transports.forEach { transportManager.removeTransport(it.id) }
                    }
                }
            }
        })


        Disposer.register(this, model)

    }

    private fun showContextMenu(rows: IntArray, e: MouseEvent) {
        val transports = rows.map { getPathForRow(it).lastPathComponent }
            .filterIsInstance<TransportTreeTableNode>().map { it.transport }
        val popupMenu = FlatPopupMenu()

        val delete = popupMenu.add(I18n.getString("termora.transport.jobs.contextmenu.delete"))
        val deleteAll = popupMenu.add(I18n.getString("termora.transport.jobs.contextmenu.delete-all"))
        delete.addActionListener {
            if (OptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    I18n.getString("termora.keymgr.delete-warning"),
                    messageType = JOptionPane.WARNING_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                for (transport in transports) {
                    transportManager.removeTransport(transport.id)
                }
            }
        }
        deleteAll.addActionListener {
            if (OptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    I18n.getString("termora.keymgr.delete-warning"),
                    messageType = JOptionPane.WARNING_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                transportManager.removeTransport(0)
            }
        }

        delete.isEnabled = transports.isNotEmpty()

        popupMenu.show(this, e.x, e.y)
    }

    private suspend fun refreshView() {
        while (coroutineScope.isActive) {
            for (row in 0 until rowCount) {
                val treePath = getPathForRow(row) ?: continue
                val node = treePath.lastPathComponent as? TransportTreeTableNode ?: continue
                model.valueForPathChanged(treePath, node.transport)
            }
            delay(SpeedReporter.millis.milliseconds)
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

}