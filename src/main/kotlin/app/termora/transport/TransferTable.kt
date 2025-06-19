package app.termora.transport

import app.termora.Disposable
import app.termora.NativeIcons
import com.formdev.flatlaf.FlatClientProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXTreeTable
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import java.awt.Component
import java.awt.Insets
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.TreeNode
import kotlin.io.path.name
import kotlin.time.Duration.Companion.milliseconds


class TransferTable(private val coroutineScope: CoroutineScope, private val tableModel: TransferTableModel) :
    JXTreeTable(), Disposable {

    private val lru = object : LinkedHashMap<TreeNode, Pair<String, Long>>() {
        override fun removeEldestEntry(eldest: Map.Entry<TreeNode?, Pair<String, Long>?>?): Boolean {
            return size > 128
        }
    }

    init {
        initView()
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
    }


    private fun refreshView() {
        coroutineScope.launch(Dispatchers.Swing) {
            val timeout = 500
            while (coroutineScope.isActive) {
                for (row in 0 until rowCount) {
                    val treePath = getPathForRow(row) ?: continue
                    val node = treePath.lastPathComponent as? DefaultMutableTreeTableNode ?: continue
                    tableModel.valueForPathChanged(treePath, node.userObject)
                }
                delay(timeout.milliseconds)
            }
        }
    }
}

