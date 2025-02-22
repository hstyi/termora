package app.termora

import app.termora.Application.ohMyJson
import app.termora.actions.AnActionEvent
import app.termora.actions.OpenHostAction
import app.termora.transport.SFTPAction
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeOpenIcon
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.ini4j.Ini
import org.jdesktop.swingx.JXTree
import org.jdesktop.swingx.action.ActionManager
import org.jdesktop.swingx.tree.DefaultXTreeCellRenderer
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.*
import java.util.*
import java.util.function.Function
import javax.swing.*
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.math.min

class NewHostTree : JXTree() {

    companion object {
        private val log = LoggerFactory.getLogger(NewHostTree::class.java)
        private val CSV_HEADERS = arrayOf("Folders", "Label", "Hostname", "Port", "Username", "Protocol")
    }

    private val tree = this
    private val editor = OutlineTextField(64)
    private val hostManager get() = HostManager.getInstance()
    private val properties get() = Database.getDatabase().properties
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val openHostAction get() = ActionManager.getInstance().getAction(OpenHostAction.OPEN_HOST)
    private var isShowMoreInfo
        get() = properties.getString("HostTree.showMoreInfo", "false").toBoolean()
        set(value) = properties.putString("HostTree.showMoreInfo", value.toString())

    private val model = NewHostTreeModel()

    /**
     * 是否允许显示右键菜单
     */
    var contextmenu = true

    /**
     * 是否允许双击连接
     */
    var doubleClickConnection = true

    init {
        initViews()
        initEvents()
    }


    private fun initViews() {
        super.setModel(model)
        isEditable = true
        dragEnabled = true
        isRootVisible = true
        dropMode = DropMode.ON_OR_INSERT
        selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        editor.preferredSize = Dimension(220, 0)

        // renderer
        setCellRenderer(object : DefaultXTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree,
                value: Any,
                sel: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): Component {
                val node = value as HostTreeNode
                val host = node.host
                var text = host.name

                // 是否显示更多信息
                if (isShowMoreInfo) {
                    val color = if (sel) {
                        if (tree.hasFocus()) {
                            UIManager.getColor("textHighlightText")
                        } else {
                            this.foreground
                        }
                    } else {
                        UIManager.getColor("textInactiveText")
                    }

                    val fontTag = Function<String, String> {
                        """<font color=rgb(${color.red},${color.green},${color.blue})>${it}</font>"""
                    }

                    if (host.protocol == Protocol.SSH) {
                        text =
                            "<html>${host.name}&nbsp;&nbsp;&nbsp;&nbsp;${fontTag.apply("${host.username}@${host.host}")}</html>"
                    } else if (host.protocol == Protocol.Serial) {
                        text =
                            "<html>${host.name}&nbsp;&nbsp;&nbsp;&nbsp;${fontTag.apply(host.options.serialComm.port)}</html>"
                    } else if (host.protocol == Protocol.Folder) {
                        text = "<html>${host.name}${fontTag.apply(" (${node.childCount})")}</html>"
                    }
                }

                val c = super.getTreeCellRendererComponent(tree, text, sel, expanded, leaf, row, hasFocus)

                icon = when (host.protocol) {
                    Protocol.Folder -> if (expanded) FlatTreeOpenIcon() else FlatTreeClosedIcon()
                    Protocol.Serial -> if (sel && tree.hasFocus()) Icons.plugin.dark else Icons.plugin
                    else -> if (sel && tree.hasFocus()) Icons.terminal.dark else Icons.terminal
                }
                return c
            }
        })

        // rename
        setCellEditor(object : DefaultCellEditor(editor) {
            override fun isCellEditable(e: EventObject?): Boolean {
                if (e is MouseEvent) {
                    return false
                }
                return super.isCellEditable(e)
            }

            override fun getCellEditorValue(): Any {
                val node = lastSelectedPathComponent as HostTreeNode
                return node.host
            }
        })
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

                if (contextmenu) {
                    SwingUtilities.invokeLater { showContextmenu(e) }
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (doubleClickConnection && SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val lastNode = lastSelectedPathComponent as? HostTreeNode ?: return
                    if (lastNode.host.protocol != Protocol.Folder) {
                        openHostAction?.actionPerformed(OpenHostActionEvent(e.source, lastNode.host, e))
                    }
                }
            }
        })

        // rename
        getCellEditor().addCellEditorListener(object : CellEditorListener {
            override fun editingStopped(e: ChangeEvent) {
                val lastHost = lastSelectedPathComponent
                if (lastHost !is HostTreeNode || editor.text.isBlank() || editor.text == lastHost.host.name) {
                    return
                }
                lastHost.host = lastHost.host.copy(name = editor.text, updateDate = System.currentTimeMillis())
                hostManager.addHost(lastHost.host)
            }

            override fun editingCanceled(e: ChangeEvent) {
            }
        })

        // drag
        transferHandler = object : TransferHandler() {

            override fun createTransferable(c: JComponent): Transferable? {
                val nodes = getSelectionHostTreeNodes().toMutableList()
                if (nodes.isEmpty()) return null
                if (nodes.contains(model.root)) return null

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
                if (support.component != tree) return false
                val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                val node = dropLocation.path.lastPathComponent as? HostTreeNode ?: return false
                if (!support.isDataFlavorSupported(MoveHostTransferable.dataFlavor)) return false
                val nodes = (support.transferable.getTransferData(MoveHostTransferable.dataFlavor) as? List<*>)
                    ?.filterIsInstance<HostTreeNode>() ?: return false
                if (nodes.isEmpty()) return false
                if (node.host.protocol != Protocol.Folder) return false

                for (e in nodes) {
                    // 禁止拖拽到自己的子下面
                    if (dropLocation.path.equals(TreePath(e.path)) || TreePath(e.path).isDescendant(dropLocation.path)) {
                        return false
                    }

                    // 文件夹只能拖拽到文件夹的下面
                    if (e.host.protocol == Protocol.Folder) {
                        if (dropLocation.childIndex > node.folderCount) {
                            return false
                        }
                    } else if (dropLocation.childIndex != -1) {
                        // 非文件夹也不能拖拽到文件夹的上面
                        if (dropLocation.childIndex < node.folderCount) {
                            return false
                        }
                    }

                    val p = e.parent ?: continue
                    // 如果是同级目录排序，那么判断是不是自己的上下，如果是的话也禁止
                    if (p == node && dropLocation.childIndex != -1) {
                        val idx = p.getIndex(e)
                        if (dropLocation.childIndex in idx..idx + 1) {
                            return false
                        }
                    }
                }

                support.setShowDropLocation(true)

                return true
            }

            override fun importData(support: TransferSupport): Boolean {
                val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
                val node = dropLocation.path.lastPathComponent as? HostTreeNode ?: return false
                val nodes = (support.transferable.getTransferData(MoveHostTransferable.dataFlavor) as? List<*>)
                    ?.filterIsInstance<HostTreeNode>() ?: return false

                // 展开的 host id
                val expanded = mutableSetOf(node.host.id)
                for (e in nodes) {
                    e.getAllChildren().filter { isExpanded(TreePath(model.getPathToRoot(it))) }
                        .map { it.host.id }.forEach { expanded.add(it) }
                }

                // 转移
                for (e in nodes) {
                    model.removeNodeFromParent(e)
                    e.host = e.host.copy(parentId = node.host.id, updateDate = System.currentTimeMillis())
                    hostManager.addHost(e.host)

                    if (dropLocation.childIndex == -1) {
                        if (e.host.protocol == Protocol.Folder) {
                            model.insertNodeInto(e, node, node.folderCount)
                        } else {
                            model.insertNodeInto(e, node, node.childCount)
                        }
                    } else {
                        if (e.host.protocol == Protocol.Folder) {
                            model.insertNodeInto(e, node, min(node.folderCount, dropLocation.childIndex))
                        } else {
                            model.insertNodeInto(e, node, min(node.childCount, dropLocation.childIndex))
                        }
                    }

                    selectionPath = TreePath(model.getPathToRoot(e))
                }

                // 先展开最顶级的
                expandPath(TreePath(model.getPathToRoot(node)))

                for (child in node.getAllChildren()) {
                    if (expanded.contains(child.host.id)) {
                        expandPath(TreePath(model.getPathToRoot(child)))
                    }
                }



                return true
            }
        }
    }


    private fun showContextmenu(event: MouseEvent) {
        val lastNode = lastSelectedPathComponent
        if (lastNode !is HostTreeNode) return

        val lastNodeParent = lastNode.parent ?: model.root
        val lastHost = lastNode.host

        val popupMenu = FlatPopupMenu()
        val newMenu = JMenu(I18n.getString("termora.welcome.contextmenu.new"))
        val newFolder = newMenu.add(I18n.getString("termora.welcome.contextmenu.new.folder"))
        val newHost = newMenu.add(I18n.getString("termora.welcome.contextmenu.new.host"))
        val importMenu = JMenu(I18n.getString("termora.welcome.contextmenu.import"))
        val csvMenu = importMenu.add("CSV")
        val XshellMenu = importMenu.add("Xshell")
        val windTermMenu = importMenu.add("WindTerm")

        val open = popupMenu.add(I18n.getString("termora.welcome.contextmenu.connect"))
        val openWith = popupMenu.add(JMenu(I18n.getString("termora.welcome.contextmenu.connect-with"))) as JMenu
        val openWithSFTP = openWith.add("SFTP")
        val openWithSFTPCommand = openWith.add(I18n.getString("termora.tabbed.contextmenu.sftp-command"))
        val openInNewWindow = popupMenu.add(I18n.getString("termora.welcome.contextmenu.open-in-new-window"))
        popupMenu.addSeparator()
        val copy = popupMenu.add(I18n.getString("termora.welcome.contextmenu.copy"))
        val remove = popupMenu.add(I18n.getString("termora.welcome.contextmenu.remove"))
        val rename = popupMenu.add(I18n.getString("termora.welcome.contextmenu.rename"))
        popupMenu.addSeparator()
        val refresh = popupMenu.add(I18n.getString("termora.welcome.contextmenu.refresh"))
        val expandAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.expand-all"))
        val colspanAll = popupMenu.add(I18n.getString("termora.welcome.contextmenu.collapse-all"))
        popupMenu.addSeparator()
        popupMenu.add(importMenu)
        popupMenu.add(newMenu)
        popupMenu.addSeparator()
        val showMoreInfo = JCheckBoxMenuItem(I18n.getString("termora.welcome.contextmenu.show-more-info"))
        showMoreInfo.isSelected = isShowMoreInfo
        showMoreInfo.addActionListener {
            isShowMoreInfo = !isShowMoreInfo
            SwingUtilities.updateComponentTreeUI(tree)
        }
        popupMenu.add(showMoreInfo)
        val property = popupMenu.add(I18n.getString("termora.welcome.contextmenu.property"))

        XshellMenu.addActionListener { importHosts(lastNode, ImportType.Xshell) }
        csvMenu.addActionListener { importHosts(lastNode, ImportType.CSV) }
        windTermMenu.addActionListener { importHosts(lastNode, ImportType.WindTerm) }
        open.addActionListener { openHosts(it, false) }
        openInNewWindow.addActionListener { openHosts(it, true) }
        openWithSFTP.addActionListener { openWithSFTP(it) }
        openWithSFTPCommand.addActionListener { openWithSFTPCommand(it) }
        newFolder.addActionListener {
            val host = Host(
                id = UUID.randomUUID().toSimpleString(),
                protocol = Protocol.Folder,
                name = I18n.getString("termora.welcome.contextmenu.new.folder.name"),
                sort = System.currentTimeMillis(),
                parentId = lastHost.id
            )
            hostManager.addHost(host)
            val newNode = HostTreeNode(host)
            model.insertNodeInto(newNode, lastNode, lastNode.folderCount)
            selectionPath = TreePath(model.getPathToRoot(newNode))
            startEditingAtPath(selectionPath)
        }
        remove.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                val nodes = getSelectionHostTreeNodes()
                if (nodes.isEmpty()) return
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(tree),
                        I18n.getString("termora.keymgr.delete-warning"),
                        I18n.getString("termora.remove"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                    ) == JOptionPane.YES_OPTION
                ) {
                    for (c in nodes) {
                        hostManager.addHost(c.host.copy(deleted = true, updateDate = System.currentTimeMillis()))
                        model.removeNodeFromParent(c)
                        // 将所有子孙也删除
                        for (child in c.getAllChildren()) {
                            hostManager.addHost(
                                child.host.copy(
                                    deleted = true,
                                    updateDate = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
        })
        copy.addActionListener {
            for (c in getSelectionHostTreeNodes()) {
                val p = c.parent ?: continue
                val newNode = copyNode(c, p.host.id)
                model.insertNodeInto(newNode, p, lastNodeParent.getIndex(c) + 1)
                selectionPath = TreePath(model.getPathToRoot(newNode))
            }
        }
        rename.addActionListener { startEditingAtPath(TreePath(model.getPathToRoot(lastNode))) }
        expandAll.addActionListener {
            for (node in getSelectionHostTreeNodes(true)) {
                expandPath(TreePath(model.getPathToRoot(node)))
            }
        }
        colspanAll.addActionListener {
            for (node in getSelectionHostTreeNodes(true).reversed()) {
                collapsePath(TreePath(model.getPathToRoot(node)))
            }
        }
        newHost.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = HostDialog(owner)
                dialog.setLocationRelativeTo(owner)
                dialog.isVisible = true
                val host = (dialog.host ?: return).copy(parentId = lastHost.id)
                hostManager.addHost(host)
                val newNode = HostTreeNode(host)
                model.insertNodeInto(newNode, lastNode, lastNode.childCount)
                selectionPath = TreePath(model.getPathToRoot(newNode))
            }
        })
        property.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                val dialog = HostDialog(owner, lastHost)
                dialog.title = lastHost.name
                dialog.setLocationRelativeTo(owner)
                dialog.isVisible = true
                val host = dialog.host ?: return
                lastNode.host = host
                hostManager.addHost(host)
                model.nodeStructureChanged(lastNode)
            }
        })
        refresh.addActionListener {
            val expanded = mutableSetOf(lastNode.host.id)
            for (e in lastNode.getAllChildren()) {
                if (e.host.protocol == Protocol.Folder && isExpanded(TreePath(model.getPathToRoot(e)))) {
                    expanded.add(e.host.id)
                }
            }

            // 刷新
            model.reload(lastNode)

            // 先展开最顶级的
            expandPath(TreePath(model.getPathToRoot(lastNode)))

            for (child in lastNode.getAllChildren()) {
                if (expanded.contains(child.host.id)) {
                    expandPath(TreePath(model.getPathToRoot(child)))
                }
            }
        }

        newMenu.isEnabled = lastHost.protocol == Protocol.Folder
        remove.isEnabled = getSelectionHostTreeNodes().none { it == model.root }
        copy.isEnabled = remove.isEnabled
        rename.isEnabled = remove.isEnabled
        property.isEnabled = lastHost.protocol != Protocol.Folder
        refresh.isEnabled = lastHost.protocol == Protocol.Folder
        importMenu.isEnabled = lastHost.protocol == Protocol.Folder

        // 如果选中了 SSH 服务器，那么才启用
        openWithSFTP.isEnabled = getSelectionHostTreeNodes(true).map { it.host }.any { it.protocol == Protocol.SSH }
        openWithSFTPCommand.isEnabled = openWithSFTP.isEnabled
        openWith.isEnabled = openWith.menuComponents.any { it is JMenuItem && it.isEnabled }

        popupMenu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                tree.grabFocus()
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                tree.requestFocusInWindow()
            }

            override fun popupMenuCanceled(e: PopupMenuEvent) {
            }

        })


        popupMenu.show(this, event.x, event.y)
    }

    private fun copyNode(
        node: HostTreeNode,
        parentId: String,
        idGenerator: () -> String = { UUID.randomUUID().toSimpleString() },
        level: Int = 0
    ): HostTreeNode {

        val host = node.host
        val now = host.sort + 1
        val name = if (level == 0) "${host.name} ${I18n.getString("termora.welcome.contextmenu.copy")}"
        else host.name

        val newHost = host.copy(
            id = idGenerator.invoke(),
            name = name,
            parentId = parentId,
            updateDate = System.currentTimeMillis(),
            createDate = System.currentTimeMillis(),
            sort = now
        )
        val newNode = HostTreeNode(newHost)

        hostManager.addHost(newHost)

        if (host.protocol == Protocol.Folder) {
            for (child in node.children()) {
                if (child is HostTreeNode) {
                    newNode.add(copyNode(child, newHost.id, idGenerator, level + 1))
                }
            }
        }

        return newNode

    }

    /**
     * 包含孙子
     */
    fun getSelectionHostTreeNodes(include: Boolean = false): List<HostTreeNode> {
        val paths = selectionPaths ?: return emptyList()
        if (paths.isEmpty()) return emptyList()
        val nodes = mutableListOf<HostTreeNode>()
        val parents = paths.mapNotNull { it.lastPathComponent }
            .filterIsInstance<HostTreeNode>().toMutableList()

        if (include) {
            while (parents.isNotEmpty()) {
                val node = parents.removeFirst()
                nodes.add(node)
                parents.addAll(node.children().toList().filterIsInstance<HostTreeNode>())
            }
        }

        return if (include) nodes else parents
    }

    private fun openHosts(evt: EventObject, openInNewWindow: Boolean) {
        assertEventDispatchThread()
        val nodes = getSelectionHostTreeNodes(true).map { it.host }.filter { it.protocol != Protocol.Folder }
        if (nodes.isEmpty()) return
        val source = if (openInNewWindow)
            TermoraFrameManager.getInstance().createWindow().apply { isVisible = true }
        else evt.source
        nodes.forEach { openHostAction.actionPerformed(OpenHostActionEvent(source, it, evt)) }
    }

    private fun openWithSFTP(evt: EventObject) {
        val nodes = getSelectionHostTreeNodes(true).map { it.host }.filter { it.protocol == Protocol.SSH }
        if (nodes.isEmpty()) return

        val sftpAction = ActionManager.getInstance().getAction(app.termora.Actions.SFTP) as SFTPAction? ?: return
        val tab = sftpAction.openOrCreateSFTPTerminalTab(AnActionEvent(this, StringUtils.EMPTY, evt)) ?: return
        for (node in nodes) {
            sftpAction.connectHost(node, tab)
        }
    }

    private fun openWithSFTPCommand(evt: EventObject) {
        val nodes = getSelectionHostTreeNodes(true).map { it.host }.filter { it.protocol == Protocol.SSH }
        if (nodes.isEmpty()) return
        for (host in nodes) {
            openHostAction.actionPerformed(OpenHostActionEvent(this, host.copy(protocol = Protocol.SFTPPty), evt))
        }
    }

    private fun importHosts(folder: HostTreeNode, type: ImportType) {
        try {
            doImportHosts(folder, type)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            OptionPane.showMessageDialog(owner, ExceptionUtils.getMessage(e), messageType = JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun doImportHosts(folder: HostTreeNode, type: ImportType) {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        chooser.isMultiSelectionEnabled = false

        when (type) {
            ImportType.WindTerm -> chooser.fileFilter = FileNameExtensionFilter("WindTerm (*.sessions)", "sessions")
            ImportType.CSV -> chooser.fileFilter = FileNameExtensionFilter("CSV (*.csv)", "csv")
            ImportType.Xshell -> {
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Xshell Sessions"
                chooser.isAcceptAllFileFilterUsed = true
            }
        }

        val dir = properties.getString("NewHostTree.ImportHosts.defaultDir", StringUtils.EMPTY)
        if (dir.isNotBlank()) {
            val file = FileUtils.getFile(dir)
            if (file.exists()) {
                chooser.currentDirectory = file
            }
        }

        // csv template
        if (type == ImportType.CSV) {
            val code = OptionPane.showConfirmDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.csv.download-template"),
                optionType = JOptionPane.YES_NO_OPTION,
                messageType = JOptionPane.QUESTION_MESSAGE,
                options = arrayOf(
                    I18n.getString("termora.welcome.contextmenu.import"),
                    I18n.getString("termora.welcome.contextmenu.download")
                ),
                initialValue = I18n.getString("termora.welcome.contextmenu.import")
            )
            if (code == JOptionPane.DEFAULT_OPTION) {
                return
            } else if (code != JOptionPane.YES_OPTION) {
                chooser.setSelectedFile(File("termora_import.csv"))
                if (chooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
                    CSVPrinter(
                        FileWriter(chooser.selectedFile, Charsets.UTF_8),
                        CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()
                    ).use { printer ->
                        printer.printRecord("Projects/Dev", "Web Server", "192.168.1.1", "22", "root", "SSH")
                        printer.printRecord("Projects/Prod", "Web Server", "serverhost.com", "2222", "root", "SSH")
                        printer.printRecord(StringUtils.EMPTY, "Web Server", "serverhost.com", "2222", "user", "SSH")
                    }
                    OptionPane.openFileInFolder(
                        owner,
                        chooser.selectedFile,
                        I18n.getString("termora.welcome.contextmenu.import.csv.download-template-done-open-folder"),
                        I18n.getString("termora.welcome.contextmenu.import.csv.download-template-done")
                    )
                }
                return
            }
        }

        // 选择文件
        val code = chooser.showOpenDialog(owner)

        // 记住目录
        properties.putString("NewHostTree.ImportHosts.defaultDir", chooser.currentDirectory.absolutePath)

        if (code != JFileChooser.APPROVE_OPTION) {
            return
        }

        val file = chooser.selectedFile

        val nodes = when (type) {
            ImportType.WindTerm -> parseFromWindTerm(folder, file)
            ImportType.Xshell -> parseFromXshell(folder, file)
            ImportType.CSV -> file.bufferedReader().use { parseFromCSV(folder, it) }
        }

        for (node in nodes) {
            node.host = node.host.copy(parentId = folder.host.id, updateDate = System.currentTimeMillis())
            if (folder.getIndex(node) != -1) {
                continue
            }
            model.insertNodeInto(
                node,
                folder,
                if (node.host.protocol == Protocol.Folder) folder.folderCount else folder.childCount
            )
        }

        for (node in nodes) {
            hostManager.addHost(node.host)
            node.getAllChildren().forEach { hostManager.addHost(it.host) }
        }

        // 重新加载
        model.reload(folder)
    }

    private fun parseFromWindTerm(folder: HostTreeNode, file: File): List<HostTreeNode> {
        val sessions = ohMyJson.runCatching { ohMyJson.parseToJsonElement(file.readText()).jsonArray }
            .onFailure { OptionPane.showMessageDialog(owner, ExceptionUtils.getMessage(it)) }
            .getOrNull() ?: return emptyList()

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (i in 0 until sessions.size) {
                val json = sessions[i].jsonObject
                val protocol = json["session.protocol"]?.jsonPrimitive?.content ?: "SSH"
                if (!StringUtils.equalsIgnoreCase("SSH", protocol)) continue
                val label = json["session.label"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val target = json["session.target"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val port = json["session.port"]?.jsonPrimitive?.intOrNull ?: 22
                val group = json["session.group"]?.jsonPrimitive?.content ?: StringUtils.EMPTY
                val groups = group.split(">")
                printer.printRecord(groups.joinToString("/"), label, target, port, StringUtils.EMPTY, "SSH")
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromXshell(folder: HostTreeNode, dir: File): List<HostTreeNode> {
        val files = FileUtils.listFiles(dir, arrayOf("xsh"), true)
        if (files.isEmpty()) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.welcome.contextmenu.import.xshell-folder-empty")
            )
            return emptyList()
        }

        val sw = StringWriter()
        CSVPrinter(sw, CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).get()).use { printer ->
            for (file in files) {
                val ini = Ini(file)
                val protocol = ini.get("CONNECTION", "Protocol") ?: "SSH"
                if (!StringUtils.equalsIgnoreCase("SSH", protocol)) continue
                val folders = FilenameUtils.separatorsToUnix(file.parentFile.relativeTo(dir).toString())
                val hostname = ini.get("CONNECTION", "Host") ?: StringUtils.EMPTY
                val label = file.nameWithoutExtension
                val port = ini.get("CONNECTION", "Port")?.toIntOrNull() ?: 22
                val username = ini.get("CONNECTION:AUTHENTICATION", "UserName") ?: StringUtils.EMPTY
                printer.printRecord(folders, label, hostname, port, username, "SSH")
            }
        }

        return parseFromCSV(folder, StringReader(sw.toString()))
    }

    private fun parseFromCSV(folderNode: HostTreeNode, sr: Reader): List<HostTreeNode> {
        val records = CSVParser.builder()
            .setFormat(CSVFormat.EXCEL.builder().setHeader(*CSV_HEADERS).setSkipHeaderRecord(true).get())
            .setCharset(Charsets.UTF_8)
            .setReader(sr)
            .get()
            .use { it.records }
        // 把现有目录提取出来，避免重复创建
        val nodes = folderNode.clone(setOf(Protocol.Folder))
            .childrenNode().filter { it.host.protocol == Protocol.Folder }
            .toMutableList()

        for (record in records) {
            val map = mutableMapOf<String, String>()
            for (e in record.parser.headerMap.keys) {
                map[e] = record.get(e)
            }

            val folder = map["Folders"] ?: StringUtils.EMPTY
            val label = map["Label"] ?: StringUtils.EMPTY
            val hostname = map["Hostname"] ?: StringUtils.EMPTY
            val port = map["Port"]?.toIntOrNull() ?: 22
            val username = map["Username"] ?: StringUtils.EMPTY
            val protocol = map["Protocol"] ?: "SSH"
            if (!StringUtils.equalsIgnoreCase(protocol, "SSH")) continue
            if (StringUtils.isAllBlank(hostname, label)) continue

            var p: HostTreeNode? = null
            if (folder.isNotBlank()) {
                for ((j, name) in folder.split("/").withIndex()) {
                    val folders = if (j == 0 || p == null) nodes
                    else p.children().toList().filterIsInstance<HostTreeNode>()
                    val n = HostTreeNode(
                        Host(
                            name = name, protocol = Protocol.Folder,
                            parentId = p?.host?.id ?: StringUtils.EMPTY
                        )
                    )
                    val cp = folders.find { it.host.protocol == Protocol.Folder && it.host.name == name }
                    if (cp != null) {
                        p = cp
                        continue
                    }
                    if (p == null) {
                        p = n
                        nodes.add(n)
                    } else {
                        p.add(n)
                        p = n
                    }
                }
            }

            val n = HostTreeNode(
                Host(
                    name = StringUtils.defaultIfBlank(label, hostname),
                    host = hostname,
                    port = port,
                    username = username,
                    protocol = Protocol.SSH,
                    parentId = p?.host?.id ?: StringUtils.EMPTY,
                )
            )

            if (p == null) {
                nodes.add(n)
            } else {
                p.add(n)
            }
        }

        return nodes
    }


    private enum class ImportType {
        WindTerm,
        CSV,
        Xshell,
    }

    private class MoveHostTransferable(val nodes: List<HostTreeNode>) : Transferable {
        companion object {
            val dataFlavor =
                DataFlavor("${DataFlavor.javaJVMLocalObjectMimeType};class=${MoveHostTransferable::class.java.name}")
        }


        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return arrayOf(dataFlavor)
        }

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
            return dataFlavor == flavor
        }

        override fun getTransferData(flavor: DataFlavor?): Any {
            if (flavor == dataFlavor) {
                return nodes
            }
            throw UnsupportedFlavorException(flavor)
        }

    }

}