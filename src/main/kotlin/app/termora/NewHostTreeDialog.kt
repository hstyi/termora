package app.termora

import org.apache.commons.lang3.StringUtils
import java.awt.Dimension
import java.awt.Window
import java.util.function.Function
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.UIManager

class NewHostTreeDialog(
    owner: Window,
) : DialogWrapper(owner) {
    var hosts = emptyList<Host>()
    var allowMulti = true

    private var filter: Function<HostTreeNode, Boolean> = Function<HostTreeNode, Boolean> { true }
    private val tree = NewHostTree()

    init {
        size = Dimension(UIManager.getInt("Dialog.width") - 200, UIManager.getInt("Dialog.height") - 150)
        isModal = true
        isResizable = false
        controlsVisible = false
        title = I18n.getString("termora.transport.sftp.select-host")

        tree.contextmenu = false
        tree.doubleClickConnection = false
        tree.dragEnabled = false



        init()
        setLocationRelativeTo(null)

    }

    fun setFilter(filter: Function<HostTreeNode, Boolean>) {
        tree.model = FilterableHostTreeModel(tree) { false }.apply {
            addFilter(filter)
            refresh()
        }
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JScrollPane(tree)
        scrollPane.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        )

        return scrollPane
    }


    override fun doCancelAction() {
        hosts = emptyList()
        super.doCancelAction()
    }

    override fun doOKAction() {
        hosts = tree.getSelectionHostTreeNodes(true)
            .filter { filter.apply(it) }
            .map { it.host }

        if (hosts.isEmpty()) return
        if (!allowMulti && hosts.size > 1) return

        super.doOKAction()
    }

    fun setTreeName(treeName: String) {
        Disposer.register(disposable, object : Disposable {
            private val key = "${treeName}.state"
            private val properties get() = Database.getDatabase().properties

            init {
                TreeUtils.loadExpansionState(tree, properties.getString(key, StringUtils.EMPTY))
            }

            override fun dispose() {
                properties.putString(key, TreeUtils.saveExpansionState(tree))
            }
        })
    }
}