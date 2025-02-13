package app.termora


import app.termora.actions.AnActionEvent
import app.termora.actions.NewHostAction
import app.termora.actions.OpenHostAction
import app.termora.transport.SFTPAction
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeOpenIcon
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import org.jdesktop.swingx.tree.DefaultXTreeCellRenderer
import java.awt.Component
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel


class HostTree : JTree(), Disposable {
    private val hostManager get() = HostManager.getInstance()
    private val editor = OutlineTextField(64)

    var contextmenu = true

    /**
     * 双击是否打开连接
     */
    var doubleClickConnection = true

    val model = HostTreeModel()
    val searchableModel = SearchableHostTreeModel(model)

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        setModel(model)
        isEditable = true
        dropMode = DropMode.ON_OR_INSERT
        dragEnabled = true
        selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        editor.preferredSize = Dimension(220, 0)

        setCellRenderer(object : DefaultXTreeCellRenderer() {
            private val properties get() = Database.getDatabase().properties
            override fun getTreeCellRendererComponent(
                tree: JTree,
                value: Any,
                sel: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): Component {
                val host = value as Host
                var text = host.name

                // 是否显示更多信息
                if (properties.getString("HostTree.showMoreInfo", "false").toBoolean()) {
                    val color = if (sel) {
                        if (this@HostTree.hasFocus()) {
                            UIManager.getColor("textHighlightText")
                        } else {
                            this.foreground
                        }
                    } else {
                        UIManager.getColor("textInactiveText")
                    }

                    if (host.protocol == Protocol.SSH) {
                        text = """
                        <html>${host.name}
                        &nbsp;&nbsp;
                        <font color=rgb(${color.red},${color.green},${color.blue})>${host.username}@${host.host}</font></html>
                    """.trimIndent()
                    } else if (host.protocol == Protocol.Serial) {
                        text = """
                        <html>${host.name}
                        &nbsp;&nbsp;
                        <font color=rgb(${color.red},${color.green},${color.blue})>${host.options.serialComm.port}</font></html>
                    """.trimIndent()
                    }
                }

                val c = super.getTreeCellRendererComponent(tree, text, sel, expanded, leaf, row, hasFocus)

                icon = when (host.protocol) {
                    Protocol.Folder -> if (expanded) FlatTreeOpenIcon() else FlatTreeClosedIcon()
                    Protocol.SSH, Protocol.Local -> if (sel && this@HostTree.hasFocus()) Icons.terminal.dark else Icons.terminal
                    Protocol.Serial -> if (sel && this@HostTree.hasFocus()) Icons.plugin.dark else Icons.plugin
                }
                return c
            }
        })

        setCellEditor(object : DefaultCellEditor(editor) {
            override fun isCellEditable(e: EventObject?): Boolean {
                if (e is MouseEvent) {
                    return false
                }
                return super.isCellEditable(e)
            }

        })


        val state = Database.getDatabase().properties.getString("HostTreeExpansionState")
        if (state != null) {
            TreeUtils.loadExpansionState(this@HostTree, state)
        }
    }

    override fun convertValueToText(
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): String {
        if (value is Host) {
            return value.name
        }
        return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus)
    }

    private fun initEvents() {
        // 右键选中
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return
                }

                requestFocusInWindow()

                val selectionRows = selectionModel.selectionRows

                val selRow = getClosestRowForLocation(e.x, e.y)
                if (selRow < 0) {
                    selectionModel.clearSelection()
                    return
                } else if (selectionRows != null && selectionRows.contains(selRow)) {
                    return
                }

                selectionPath = getPathForLocation(e.x, e.y)

                setSelectionRow(selRow)
            }

            override fun mouseClicked(e: MouseEvent) {
                if (doubleClickConnection && SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val host = lastSelectedPathComponent
                    if (host is Host && host.protocol != Protocol.Folder) {
                        ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST)
                            ?.actionPerformed(OpenHostActionEvent(e.source, host, e))
                    }
                }
            }
        })


        // contextmenu
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!(SwingUtilities.isRightMouseButton(e))) {
                    return
                }

                if (Objects.isNull(lastSelectedPathComponent)) {
                    return
                }

                SwingUtilities.invokeLater { showContextMenu(e) }
            }
        })


        // rename
        getCellEditor().addCellEditorListener(object : CellEditorListener {
            override fun editingStopped(e: ChangeEvent) {
                val lastHost = lastSelectedPathComponent
                if (lastHost !is Host || editor.text.isBlank() || editor.text == lastHost.name) {
                    return
                }
                runCatchingHost(lastHost.copy(name = editor.text))
            }

            override fun editingCanceled(e: ChangeEvent) {
            }

        })

        // drag
        transferHandler = object : TransferHandler() {

            override fun createTransferable(c: JComponent): Transferable {
                val nodes = selectionModel.selectionPaths
                    .map { it.lastPathComponent }
                    .filterIsInstance<Host>()
                    .toMutableList()

                val iterator = nodes.iterator()
                while (iterator.hasNext()) {
                    val node = iterator.next()
                    val parents = model.getPathToRoot(node).filter { it != node }
                    if (parents.any { nodes.contains(it) }) {
                        iterator.remove()
                    }
                }

                return MoveHostTransferable(nodes)
            }

            override fun getSourceActions(c: JComponent?): Int {
                return MOVE
            }

            override fun canImport(support: TransferSupport): Boolean {
                if (!support.isDrop) {
                    return false
                }
                val dropLocation = support.dropLocation
                if (dropLocation !is JTree.DropLocation || support.component != this@HostTree
                    || dropLocation.childIndex != -1
                ) {
                    return false
                }

                val lastNode = dropLocation.path.lastPathComponent
                if (lastNode !is Host || lastNode.protocol != Protocol.Folder) {
                    return false
                }

                if (support.isDataFlavorSupported(MoveHostTransferable.dataFlavor)) {
                    val nodes = support.transferable.getTransferData(MoveHostTransferable.dataFlavor) as List<*>
                    if (nodes.any { it == lastNode }) {
                        return false
                    }
                    for (parent in model.getPathToRoot(lastNode).filter { it != lastNode }) {
                        if (nodes.any { it == parent }) {
                            return false
                        }
                    }
                }
                support.setShowDropLocation(true)
                return support.isDataFlavorSupported(MoveHostTransferable.dataFlavor)
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!support.isDrop) {
                    return false
                }

                val dropLocation = support.dropLocation
                if (dropLocation !is JTree.DropLocation) {
                    return false
                }

                val lastNode = dropLocation.path.lastPathComponent
                if (lastNode !is Host || lastNode.protocol != Protocol.Folder) {
                    return false
                }

                if (!support.isDataFlavorSupported(MoveHostTransferable.dataFlavor)) {
                    return false
                }

                val hosts = (support.transferable.getTransferData(MoveHostTransferable.dataFlavor) as List<*>)
                    .filterIsInstance<Host>().toMutableList()
                if (hosts.isEmpty()) {
                    return false
                }

                // 记录展开的节点
                val expandedHosts = mutableListOf<String>()
                for (host in hosts) {
                    model.visit(host) {
                        if (it.protocol == Protocol.Folder) {
                            if (isExpanded(TreePath(model.getPathToRoot(it)))) {
                                expandedHosts.addFirst(it.id)
                            }
                        }
                    }
                }

                var now = System.currentTimeMillis()
                for (host in hosts) {
                    model.removeNodeFromParent(host)
                    val newHost = host.copy(
                        parentId = lastNode.id,
                        sort = ++now,
                        updateDate = now
                    )
                    runCatchingHost(newHost)
                }

                expandNode(lastNode)

                // 展开
                for (id in expandedHosts) {
                    model.getHost(id)?.let { expandNode(it) }
                }

                return true
            }
        }

    }

    override fun isPathEditable(path: TreePath?): Boolean {
        if (path == null) return false
        if (path.lastPathComponent == model.root) return false
        return super.isPathEditable(path)
    }

    override fun getLastSelectedPathComponent(): Any? {
        val last = super.getLastSelectedPathComponent() ?: return null
        if (last is Host) {
            return model.getHost(last.id) ?: last
        }
        return last
    }

    private fun showContextMenu(event: MouseEvent) {
        if (!contextmenu) return

        val lastHost = lastSelectedPathComponent
        if (lastHost !is Host) {
            return
        }

        val properties = Database.getDatabase().properties
        val popupMenu = FlatPopupMenu()
        val newMenu = JMenu(I18n.getString("termora.welcome.contextmenu.new"))
        val newFolder = newMenu.add(I18n.getString("termora.welcome.contextmenu.new.folder"))
        val newHost = newMenu.add(I18n.getString("termora.welcome.contextmenu.new.host"))

        val open = popupMenu.add(I18n.getString("termora.welcome.contextmenu.open"))
        val openWithSFTP = popupMenu.add(I18n.getString("termora.welcome.contextmenu.open-with-sftp"))
        val openInNewWindow = popupMenu.add(I18n.getString("termora.welcome.contextmenu.open-in-new-window"))
        popupMenu.addSeparator()
        val copy = popupMenu.add(I18n.getString("termora.welcome.contextmenu.copy"))
        val remove = popupMenu.add(I18n.getString("termora.welcome.contextmenu.remove"))
        val rename = popupMenu.add(I18n.getString("termora.welcome.contextmenu.rename"))
        popupMenu.addSeparator()
        val expandAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.expand-all"))
        val colspanAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.collapse-all"))
        popupMenu.addSeparator()
        popupMenu.add(newMenu)
        popupMenu.addSeparator()

        val showMoreInfo = JCheckBoxMenuItem(I18n.getString("termora.welcome.contextmenu.show-more-info"))
        showMoreInfo.isSelected = properties.getString("HostTree.showMoreInfo", "false").toBoolean()
        showMoreInfo.addActionListener {
            properties.putString(
                "HostTree.showMoreInfo",
                showMoreInfo.isSelected.toString()
            )
            SwingUtilities.updateComponentTreeUI(this)
        }
        popupMenu.add(showMoreInfo)
        val property = popupMenu.add(I18n.getString("termora.welcome.contextmenu.property"))

        open.addActionListener { openHosts(it, false) }
        openWithSFTP.addActionListener { openWithSFTP(it) }
        openInNewWindow.addActionListener { openHosts(it, true) }

        rename.addActionListener {
            startEditingAtPath(TreePath(model.getPathToRoot(lastHost)))
        }

        expandAll.addActionListener {
            getSelectionNodes().forEach { expandNode(it, true) }
        }


        colspanAll.addActionListener {
            selectionModel.selectionPaths.map { it.lastPathComponent }
                .filterIsInstance<Host>()
                .filter { it.protocol == Protocol.Folder }
                .forEach { collapseNode(it) }
        }

        copy.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val parent = model.getParent(lastHost) ?: return
                val node = copyNode(parent, lastHost)
                selectionPath = TreePath(model.getPathToRoot(node))
            }
        })

        remove.addActionListener {
            if (OptionPane.showConfirmDialog(
                    SwingUtilities.getWindowAncestor(this),
                    I18n.getString("termora.keymgr.delete-warning"),
                    I18n.getString("termora.remove"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                ) == JOptionPane.YES_OPTION
            ) {
                var lastParent: Host? = null
                while (!selectionModel.isSelectionEmpty) {
                    val host = lastSelectedPathComponent ?: break
                    if (host !is Host) {
                        break
                    } else {
                        lastParent = model.getParent(host)
                    }
                    model.visit(host) { hostManager.removeHost(it.id) }
                }
                if (lastParent != null) {
                    selectionPath = TreePath(model.getPathToRoot(lastParent))
                }
            }
        }

        newFolder.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (lastHost.protocol != Protocol.Folder) {
                    return
                }

                val host = Host(
                    id = UUID.randomUUID().toSimpleString(),
                    protocol = Protocol.Folder,
                    name = I18n.getString("termora.welcome.contextmenu.new.folder.name"),
                    sort = System.currentTimeMillis(),
                    parentId = lastHost.id
                )

                runCatchingHost(host)

                expandNode(lastHost)
                selectionPath = TreePath(model.getPathToRoot(host))
                startEditingAtPath(selectionPath)

            }
        })

        newHost.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                ActionManager.getInstance().getAction(NewHostAction.NEW_HOST)
                    ?.actionPerformed(e)
            }
        })

        property.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = HostDialog(SwingUtilities.getWindowAncestor(this@HostTree), lastHost)
                dialog.isVisible = true
                val host = dialog.host ?: return
                runCatchingHost(host)
            }
        })

        // 初始化状态
        newFolder.isEnabled = lastHost.protocol == Protocol.Folder
        newHost.isEnabled = newFolder.isEnabled
        remove.isEnabled = !getSelectionNodes().any { it == model.root }
        copy.isEnabled = remove.isEnabled
        rename.isEnabled = remove.isEnabled
        property.isEnabled = lastHost.protocol != Protocol.Folder

        popupMenu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                this@HostTree.grabFocus()
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                this@HostTree.requestFocusInWindow()
            }

            override fun popupMenuCanceled(e: PopupMenuEvent) {
            }

        })


        popupMenu.show(this, event.x, event.y)
    }

    private fun openHosts(evt: EventObject, openInNewWindow: Boolean) {
        assertEventDispatchThread()
        val nodes = getSelectionNodes().filter { it.protocol != Protocol.Folder }
        if (nodes.isEmpty()) return
        val openHostAction = ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST) ?: return
        val source = if (openInNewWindow)
            TermoraFrameManager.getInstance().createWindow().apply { isVisible = true }
        else evt.source

        nodes.forEach { openHostAction.actionPerformed(OpenHostActionEvent(source, it, evt)) }
    }

    private fun openWithSFTP(evt: EventObject) {
        val nodes = getSelectionNodes().filter { it.protocol == Protocol.SSH }
        if (nodes.isEmpty()) return

        val sftpAction = ActionManager.getInstance().getAction(Actions.SFTP) as SFTPAction? ?: return
        val tab = sftpAction.openOrCreateSFTPTerminalTab(AnActionEvent(this, StringUtils.EMPTY, evt)) ?: return
        for (node in nodes) {
            sftpAction.connectHost(node, tab)
        }
    }

    fun expandNode(node: Host, including: Boolean = false) {
        expandPath(TreePath(model.getPathToRoot(node)))
        if (including) {
            model.getChildren(node).forEach { expandNode(it, true) }
        }
    }


    private fun copyNode(
        parent: Host,
        host: Host,
        idGenerator: () -> String = { UUID.randomUUID().toSimpleString() }
    ): Host {
        val now = System.currentTimeMillis()
        val newHost = host.copy(
            name = "${host.name} ${I18n.getString("termora.welcome.contextmenu.copy")}",
            id = idGenerator.invoke(),
            parentId = parent.id,
            updateDate = now,
            createDate = now,
            sort = now
        )

        runCatchingHost(newHost)

        if (host.protocol == Protocol.Folder) {
            for (child in model.getChildren(host)) {
                copyNode(newHost, child, idGenerator)
            }
            if (isExpanded(TreePath(model.getPathToRoot(host)))) {
                expandNode(newHost)
            }
        }

        return newHost

    }

    private fun runCatchingHost(host: Host) {
        hostManager.addHost(host)
    }

    private fun collapseNode(node: Host) {
        model.getChildren(node).forEach { collapseNode(it) }
        collapsePath(TreePath(model.getPathToRoot(node)))
    }

    fun getSelectionNodes(): List<Host> {
        val selectionNodes = selectionModel.selectionPaths.map { it.lastPathComponent }
            .filterIsInstance<Host>()

        if (selectionNodes.isEmpty()) {
            return emptyList()
        }

        val nodes = mutableListOf<Host>()
        val parents = mutableListOf<Host>()

        for (node in selectionNodes) {
            if (node.protocol == Protocol.Folder) {
                parents.add(node)
            }
            nodes.add(node)
        }

        while (parents.isNotEmpty()) {
            val p = parents.removeFirst()
            for (i in 0 until getModel().getChildCount(p)) {
                val child = getModel().getChild(p, i) as Host
                nodes.add(child)
                parents.add(child)
            }
        }

        // 确保是最新的
        for (i in 0 until nodes.size) {
            nodes[i] = model.getHost(nodes[i].id) ?: continue
        }

        return nodes
    }

    override fun dispose() {
        Database.getDatabase().properties.putString(
            "HostTreeExpansionState",
            TreeUtils.saveExpansionState(this)
        )
    }

    private abstract class HostTreeNodeTransferable(val hosts: List<Host>) :
        Transferable {

        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return arrayOf(getDataFlavor())
        }

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
            return getDataFlavor() == flavor
        }

        override fun getTransferData(flavor: DataFlavor): Any {
            return hosts
        }

        abstract fun getDataFlavor(): DataFlavor
    }

    private class MoveHostTransferable(hosts: List<Host>) : HostTreeNodeTransferable(hosts) {
        companion object {
            val dataFlavor =
                DataFlavor("${DataFlavor.javaJVMLocalObjectMimeType};class=${MoveHostTransferable::class.java.name}")
        }

        override fun getDataFlavor(): DataFlavor {
            return dataFlavor
        }

    }


}