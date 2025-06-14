package app.termora.tree

import app.termora.*
import app.termora.database.DatabaseManager
import org.apache.commons.lang3.StringUtils
import java.awt.Dimension
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class NewHostTreeDialog(
    owner: Window,
    filter: Filter = object : Filter {
        override fun filter(node: Any): Boolean {
            return true
        }
    }
) : DialogWrapper(owner) {
    var hosts = emptyList<Host>()
    var allowMulti = true

    private val tree = NewHostTree()

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 250, UIManager.getInt("Dialog.height") - 150)
        isModal = true
        isResizable = false
        controlsVisible = false
        title = I18n.getString("termora.transport.sftp.select-host")

        tree.contextmenu = false
        tree.doubleClickConnection = false
        tree.dragEnabled = false
        tree.showsRootHandles = true

        val model = FilterableTreeModel(tree)
        model.addFilter(filter)
        tree.model = model


        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val node = tree.getLastSelectedPathNode() ?: return
                    if (node.isFolder) return
                    doOKAction()
                }
            }
        })

        Disposer.register(disposable, tree)
        Disposer.register(tree, model)

        init()
        setLocationRelativeTo(owner)

    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JScrollPane(tree)
        scrollPane.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.Companion.BorderColor),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        )

        return scrollPane
    }


    override fun doCancelAction() {
        hosts = emptyList()
        super.doCancelAction()
    }

    override fun doOKAction() {
        hosts = tree.getSelectionSimpleTreeNodes(true)
            .map { it.host }

        if (hosts.isEmpty()) return
        if (!allowMulti && hosts.size > 1) return

        super.doOKAction()
    }

    fun setTreeName(treeName: String) {
        Disposer.register(disposable, object : Disposable {
            private val key = "${treeName}.state"
            private val properties get() = DatabaseManager.getInstance().properties

            init {
                TreeUtils.loadExpansionState(tree, properties.getString(key, StringUtils.EMPTY))
            }

            override fun dispose() {
                properties.putString(key, TreeUtils.saveExpansionState(tree))
            }
        })
    }
}