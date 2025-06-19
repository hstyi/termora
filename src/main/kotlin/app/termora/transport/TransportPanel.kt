package app.termora.transport

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.sftp.FileSystemViewTableModel
import app.termora.vfs2.sftp.MySftpFileObject
import app.termora.vfs2.sftp.MySftpFileSystem
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeLeafIcon
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.sshd.sftp.client.fs.WithFileAttributes
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXPanel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
import java.awt.Rectangle
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileSystemView
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import javax.swing.undo.AbstractUndoableEdit
import javax.swing.undo.UndoManager
import javax.swing.undo.UndoableEdit
import kotlin.io.path.absolutePathString
import kotlin.io.path.fileAttributesView
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds


class TransportPanel(
    private val coroutineScope: CoroutineScope,
    private val transferManager: InternalTransferManager,
    host: Host,
    val loader: TransportSupportLoader,
) : JPanel(BorderLayout()), DataProvider, Disposable, TransportNavigator {
    companion object {
        private val log = LoggerFactory.getLogger(TransportPanel::class.java)
        private val folderIcon = FlatTreeClosedIcon()
        private val fileIcon = FlatTreeLeafIcon()

        internal fun FileSystem.isWindowsFileSystem(): Boolean {
            return SystemInfo.isWindows && isLocallyFileSystem()
        }

        internal fun FileSystem.isLocallyFileSystem(): Boolean {
            return this == FileSystems.getDefault()
        }

    }

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val lru = object : LinkedHashMap<String, Icon?>() {
        override fun removeEldestEntry(eldest: Map.Entry<String?, Icon?>?): Boolean {
            return size > 2048
        }
    }

    val toolbar = FlatToolBar()

    private val homeBtn = JButton(Icons.homeFolder)
    private val prevBtn = JButton(Icons.left)
    private val nextBtn = JButton(Icons.right)
    private val eyeBtn = JButton(Icons.eye)
    private val parentBtn = JButton(Icons.up)
    private val refreshBtn = JButton(Icons.refresh)

    private val layeredPane = LayeredPane()
    private val loadingPanel = LoadingPanel()
    private val model = MyModel()
    private val table = JTable(model)
    private val sorter = TableRowSorter(table.model)
    private var hasParent = false

    private val enableManager get() = EnableManager.getInstance()
    private val showHiddenFilesKey = "termora.transport.host.${host.id}.show-hidden-files"
    private var showHiddenFiles: Boolean
        get() = enableManager.getFlag(showHiddenFilesKey, true)
        set(value) = enableManager.setFlag(showHiddenFilesKey, value)
    private val navigator get() = this
    private val nextReloadCallbacks = mutableListOf<() -> Unit>()
    private val history = linkedSetOf<Path>()
    private val undoManager = MyUndoManager()

    private val fileSystem by lazy { getSupport().fileSystem }
    private val defaultPath by lazy { getSupport().path }


    /**
     * 工作目录
     */
    override var workdir: Path? = null
        private set

    override var loading = false
        private set(value) {
            val oldValue = field
            field = value
            if (oldValue != value) {
                firePropertyChange("loading", oldValue, value)
            }
        }

    init {
        initView()
        initEvents()
        initTableEvents()
    }

    private fun initView() {

        prevBtn.isEnabled = false
        nextBtn.isEnabled = false

        eyeBtn.icon = if (showHiddenFiles) Icons.eye else Icons.eyeClose

        toolbar.add(prevBtn)
        toolbar.add(homeBtn)
        toolbar.add(nextBtn)
        toolbar.add(TransportNavigationPanel(loader, this))
        toolbar.add(parentBtn)
        toolbar.add(eyeBtn)
        toolbar.add(refreshBtn)

        sorter.maxSortKeys = 1
        table.setRowSorter(sorter)
        table.setAutoCreateRowSorter(false)
        table.getTableHeader().setReorderingAllowed(false)
        table.setDragEnabled(true)
        table.setDropMode(DropMode.ON_OR_INSERT_ROWS)
        table.setCellSelectionEnabled(false)
        table.setRowSelectionAllowed(true)
        table.setRowHeight(UIManager.getInt("Table.rowHeight"))
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
        table.setFillsViewportHeight(true)
        table.setShowGrid(true)
        table.showVerticalLines = true
        table.showHorizontalLines = true
        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "cellMargins" to Insets(0, 4, 0, 4),
            )
        )
        table.columnModel.getColumn(FileSystemViewTableModel.COLUMN_NAME).preferredWidth = 220
        table.columnModel.getColumn(FileSystemViewTableModel.COLUMN_LAST_MODIFIED_TIME).preferredWidth = 120


        val sorts = mutableMapOf(
            // @formatter:off
            MyModel.COLUMN_NAME to Comparator<Attributes> { o1, o2 -> NativeStringComparator.getInstance().compare(o1.name, o2.name) },
            MyModel.COLUMN_FILE_SIZE to Comparator<Attributes> { o1, o2 -> o1.fileSize.compareTo(o2.fileSize) },
            MyModel.COLUMN_TYPE to Comparator<Attributes> { o1, o2 -> o1.type.compareTo(o2.type) },
            MyModel.COLUMN_LAST_MODIFIED_TIME to Comparator<Attributes> { o1, o2 -> o1.lastModifiedTime.compareTo(o2.lastModifiedTime) },
            MyModel.COLUMN_OWNER to Comparator<Attributes> { o1, o2 -> o1.owner.compareTo(o2.owner) },
            MyModel.COLUMN_ATTRS to Comparator<Attributes> { o1, o2 -> PosixFilePermissions.toString(o1.permissions).compareTo(PosixFilePermissions.toString(o2.permissions)) },
            // @formatter:on
        )

        for (i in 0 until table.columnCount) {
            sorter.setSortable(i, false)
        }

        for (e in sorts) {
            sorter.setSortable(e.key, true)
            // @formatter:off
            sorter.setComparator(e.key, Comparator<Attributes> { o1, o2 -> navigator.compare(o1, o2) ?: e.value.compare(o1, o2) })
            // @formatter:on
        }


        table.setDefaultRenderer(Any::class.java, MyDefaultTableCellRenderer())
        val scrollPane = JScrollPane(table)
        scrollPane.apply { border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor) }

        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(loadingPanel, JLayeredPane.PALETTE_LAYER as Any)

        add(toolbar, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)
    }

    private fun compare(o1: Attributes, o2: Attributes): Int? {
        val sortOrder = sorter.sortKeys.first().sortOrder
        if (sortOrder == SortOrder.ASCENDING) {
            if (o1.isParent && o2.isParent) return 0
            if (o1.isParent) return -1
            if (o2.isParent) return 1
        } else {
            if (o1.isParent && o2.isParent) return 0
            if (o1.isParent) return 1
            if (o2.isParent) return -1
        }
        return null
    }

    private fun initEvents() {

        refreshBtn.addActionListener { reload() }

        prevBtn.addActionListener { navigator.back() }
        nextBtn.addActionListener { navigator.forward() }

        parentBtn.addActionListener(createSmartAction(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (hasParent.not()) return
                navigator.navigateTo(model.getPath(0))
            }
        }))

        homeBtn.addActionListener(createSmartAction(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                navigator.navigateTo(fileSystem.getPath(defaultPath))
            }
        }))

        eyeBtn.addActionListener(createSmartAction(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                showHiddenFiles = showHiddenFiles.not()
                eyeBtn.icon = if (showHiddenFiles) Icons.eye else Icons.eyeClose
                reload()
            }
        }))


        undoManager.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                prevBtn.isEnabled = undoManager.canUndo()
                nextBtn.isEnabled = undoManager.canRedo()
            }
        })


        // parent button
        addPropertyChangeListener("loading") { evt ->
            if (evt.newValue == false) {
                parentBtn.isEnabled = hasParent
            }
        }

        // loading ui
        addPropertyChangeListener("loading", object : PropertyChangeListener {
            private var job: Job? = null
            override fun propertyChange(evt: PropertyChangeEvent) {
                job?.cancel()
                job = null
                val loading = evt.newValue == true
                if (loading) {
                    job = coroutineScope.launch(Dispatchers.Unconfined) {
                        delay(150.milliseconds)
                        withContext(Dispatchers.Swing) {
                            loadingPanel.busyLabel.isBusy = true
                            loadingPanel.isVisible = true
                        }
                    }
                } else {
                    loadingPanel.busyLabel.isBusy = false
                    loadingPanel.isVisible = false
                }
            }
        })

        // history
        addPropertyChangeListener("workdir") { evt ->
            val newValue = evt.newValue
            if (newValue is Path) {
                if ((newValue.fileSystem.isWindowsFileSystem() && newValue.fileSystem.separator == newValue.pathString).not()) {
                    history.add(newValue)
                }
            }
        }

        // undo or redo
        addPropertyChangeListener("workdir", object : PropertyChangeListener {
            private var undoOrRedo = false
            private var undoOrRedoPath: Path? = null

            override fun propertyChange(evt: PropertyChangeEvent) {
                val newValue = evt.newValue
                val oldValue = evt.oldValue
                if (newValue is Path && oldValue is Path) {
                    if (undoOrRedo && (undoOrRedoPath == newValue || undoOrRedoPath == oldValue)) {
                        undoOrRedo = false
                        return
                    }
                    undoManager.addEdit(object : AbstractUndoableEdit() {
                        override fun undo() {
                            super.undo()
                            if (navigator.navigateTo(oldValue)) {
                                undoOrRedo = true
                                undoOrRedoPath = oldValue
                            }
                        }

                        override fun redo() {
                            super.redo()
                            if (navigator.navigateTo(newValue)) {
                                undoOrRedo = true
                                undoOrRedoPath = newValue
                            }
                        }

                        override fun getPresentationName(): String {
                            return "Path"
                        }
                    })

                }
            }
        })

        addPropertyChangeListener("workdir") { evt -> reload() }

        reload()
    }

    private fun initTableEvents() {

        table.tableHeader.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val column = table.tableHeader.columnAtPoint(e.point)
                    if (column < 0) return
                    sorter.sortKeys = null
                }
            }
        })

        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    enterSelectionFolder()
                }
            }
        })

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    enterSelectionFolder()
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    val r = table.rowAtPoint(e.point)
                    if (r >= 0 && r < table.rowCount) {
                        if (!table.isRowSelected(r)) {
                            table.setRowSelectionInterval(r, r)
                        }
                    } else {
                        table.clearSelection()
                    }

                    if (table.hasFocus().not()) {
                        table.requestFocusInWindow()
                    }

                    val rows = table.selectedRows.map { sorter.convertRowIndexToModel(it) }.toTypedArray()
                    if (rows.isEmpty()) return
                    showContextmenu(rows, e)
                }
            }
        })

        table.actionMap.put("reload", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                reload()
            }
        })

        val inputMap = table.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        if (SystemInfo.isMacOS.not()) {
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "reload")
        }
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, toolkit.menuShortcutKeyMaskEx), "reload")
    }

    /**
     * 不能在 EDT 线程调用
     */
    private fun getSupport(): TransportSupport {
        if (SwingUtilities.isEventDispatchThread()) {
            throw WrongThreadException("AWT EventQueue")
        }
        return loader.get()
    }

    private fun enterSelectionFolder() {
        var row = table.selectedRow
        if (row < 0) return
        row = sorter.convertRowIndexToModel(table.selectedRow)
        val attributes = model.getAttributes(row)
        if (attributes.isDirectory.not()) return

        // 记住当前目录名称
        val path = model.getPath(row)
        if (attributes.isParent) {
            val workdir = workdir
            if (workdir != null) registerSelectRow(workdir.name)
        }

        navigator.navigateTo(path)
    }

    private fun registerSelectRow(name: String) {
        nextReloadCallbacks.add {
            for (i in 0 until model.rowCount) {
                if (model.getAttributes(i).name == name) {
                    val c = sorter.convertRowIndexToView(i)
                    table.clearSelection()
                    table.setRowSelectionInterval(c, c)
                    table.scrollRectToVisible(table.getCellRect(c, MyModel.COLUMN_NAME, true))
                    break
                }
            }
        }
    }

    private fun reload(oldPath: Path? = workdir, newPath: Path? = workdir): Boolean {
        assertEventDispatchThread()

        if (loading) return false
        loading = true

        coroutineScope.launch {
            try {

                val workdir = doReload(oldPath, newPath)

                withContext(Dispatchers.Swing) {
                    setNewWorkdir(workdir)
                    nextReloadCallbacks.forEach { runCatching { it.invoke() } }
                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
                coroutineScope.launch(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner,
                        ExceptionUtils.getRootCauseMessage(e),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                withContext(Dispatchers.Swing) {
                    loading = false
                    nextReloadCallbacks.clear()
                }
            }
        }

        return true
    }

    private suspend fun doReload(oldPath: Path? = null, newPath: Path? = null): Path {

        val workdir = newPath ?: oldPath

        if (workdir == null) {
            val path = fileSystem.getPath(defaultPath)
            return doReload(null, path)
        }

        val path = workdir
        val first = AtomicBoolean(false)
        var parent = path.parent
        if (parent == null && fileSystem.isWindowsFileSystem() && workdir.pathString != fileSystem.separator) {
            parent = fileSystem.getPath(fileSystem.separator)
        }
        val files = mutableListOf<Pair<Path, Attributes>>()
        if ((parent != null).also { hasParent = it }) {
            val attributes = getAttributes(parent)
            files.add(parent to attributes.copy(name = "..", lastModifiedTime = Long.MIN_VALUE))
        }

        val consume = suspend {
            withContext(Dispatchers.Swing) {
                if (first.compareAndSet(false, true)) {
                    model.clear()
                    table.scrollRectToVisible(Rectangle())
                }
                for (pair in files) {
                    model.addRow(arrayOf(pair.first, pair.second))
                }
            }
            files.clear()
        }

        if (fileSystem.isWindowsFileSystem() && workdir.pathString == fileSystem.separator) {
            for (path in fileSystem.rootDirectories) {
                val attributes = getAttributes(path)
                files.add(path to attributes)
            }
        } else {
            listFiles(path).use { paths ->
                for (item in paths) {
                    files.add(item)
                    if (files.size > 50) consume.invoke()
                }
            }
        }

        if (files.isNotEmpty())
            consume.invoke()

        coroutineScope.launch(Dispatchers.Swing) { table.requestFocusInWindow() }

        return workdir
    }

    private fun listFiles(path: Path): Stream<Pair<Path, Attributes>> {
        val stream = Files.list(path)
            .map { it to getAttributes(it) }
            // @formatter:off
            .sorted(compareBy<Pair<Path, Attributes>> { it.second.isDirectory.not() }.thenComparing { a, b -> NativeStringComparator.getInstance().compare(a.second.name, b.second.name) })
            // @formatter:on

        if (showHiddenFiles.not()) {
            return stream.filter { it.second.name.startsWith(".").not() }
        }

        return stream
    }

    private fun getAttributes(path: Path): Attributes {
        if (path is WithFileAttributes) {
            val attributes = path.attributes
            if (attributes != null) {
                return Attributes(
                    name = path.name,
                    type = Attributes.computeType(attributes.isSymbolicLink, attributes.isDirectory, path.name),
                    isDirectory = attributes.isDirectory,
                    isFile = attributes.isRegularFile,
                    isSymbolicLink = attributes.isSymbolicLink,
                    fileSize = attributes.size,
                    permissions = fromSftpPermissions(attributes.permissions),
                    owner = attributes.owner ?: StringUtils.EMPTY,
                    lastModifiedTime = attributes.modifyTime.toMillis()
                )
            }
        }

        val basicAttributes = runCatching { path.fileAttributesView<BasicFileAttributeView>().readAttributes() }
            .getOrNull()
        val fileOwnerAttribute = runCatching { path.fileAttributesView<FileOwnerAttributeView>().owner }
            .getOrNull()
        val posixFileAttribute = runCatching { path.fileAttributesView<PosixFileAttributeView>().readAttributes() }
            .getOrNull()

        val fileSize = basicAttributes?.size() ?: 0
        val permissions = posixFileAttribute?.permissions() ?: emptySet()
        val owner = fileOwnerAttribute?.name ?: StringUtils.EMPTY
        val lastModifiedTime = basicAttributes?.lastModifiedTime()?.toMillis() ?: 0
        val isDirectory = basicAttributes?.isDirectory ?: false
        val isSymbolicLink = basicAttributes?.isSymbolicLink ?: false

        return Attributes(
            name = StringUtils.defaultIfBlank(path.name, path.pathString),
            type = Attributes.computeType(isSymbolicLink, isDirectory, path.name),
            isDirectory = isDirectory,
            isFile = basicAttributes?.isRegularFile ?: false,
            isSymbolicLink = isSymbolicLink,
            fileSize = fileSize,
            permissions = permissions,
            owner = owner,
            lastModifiedTime = lastModifiedTime
        )
    }

    private fun createSmartAction(action: Action): Action {
        return object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (loading) return
                action.actionPerformed(e)
            }
        }
    }

    override fun dispose() {
        loadingPanel.busyLabel.isBusy = false
    }

    private fun showContextmenu(rows: Array<Int>, e: MouseEvent) {
        val files = rows.map { model.getPath(it) to model.getAttributes(it) }
        val paths = files.map { it.first }
        val hasParent = rows.contains(0)

        val popupMenu = FlatPopupMenu()
        val newMenu = JMenu(I18n.getString("termora.transport.table.contextmenu.new"))
        // 创建文件夹
        val newFolder = newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.folder"))
        // 创建文件
        val newFile = newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.file"))
        // 传输
        val transfer = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.transfer"))
        // 编辑
        val edit = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.edit"))
        popupMenu.addSeparator()
        // 复制路径
        val copyPath = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.copy-path"))

        // 如果是本地，那么支持打开本地路径
        if (fileSystem == FileSystems.getDefault()) {
            popupMenu.add(
                I18n.getString(
                    "termora.transport.table.contextmenu.open-in-folder",
                    if (SystemInfo.isMacOS) I18n.getString("termora.finder")
                    else if (SystemInfo.isWindows) I18n.getString("termora.explorer")
                    else I18n.getString("termora.folder")
                )
            ).addActionListener {
                Application.browseInFolder(File(files.last().first.absolutePathString()))
            }

        }
        popupMenu.addSeparator()

        // 重命名
        val rename = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.rename"))

        // 删除
        val delete = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.delete"))
        // rm -rf
        val rmrf = popupMenu.add(JMenuItem("rm -rf", Icons.warningIntroduction))
        // 只有 SFTP 可以
        rmrf.isVisible = fileSystem is MySftpFileSystem

        // 修改权限
        val permission = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.change-permissions"))
        permission.isEnabled = false

        // 如果是本地系统文件，那么不允许修改权限，用户应该自己修改
        if (fileSystem is MySftpFileSystem && rows.isNotEmpty()) {
            permission.isEnabled = true
        }
        popupMenu.addSeparator()

        // 刷新
        val refresh = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.refresh"))
        popupMenu.add(refresh)
        popupMenu.addSeparator()

        // 新建
        popupMenu.add(newMenu)

        // 新建文件夹
        newFolder.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
            }
        })
        // 新建文件
        newFile.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
            }
        })
        rename.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
            }
        })
        delete.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
            }
        })
        rmrf.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
            }
        })
        copyPath.addActionListener {
            val sb = StringBuilder()
            files.forEach { sb.append(it.first.absolutePathString()).appendLine() }
            sb.deleteCharAt(sb.length - 1)
            toolkit.systemClipboard.setContents(StringSelection(sb.toString()), null)
        }
        edit.addActionListener { }
        permission.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val last = files.last()
                if (last !is MySftpFileObject) return
            }
        })
        refresh.addActionListener { }
        transfer.addActionListener {
            for (e in files) {
                val future = transferManager.addTransfer(e.first, e.second.isDirectory)
            }
        }

        if (rows.isEmpty() || hasParent) {
            transfer.isEnabled = false
            rename.isEnabled = false
            delete.isEnabled = false
            edit.isEnabled = false
            rmrf.isEnabled = false
            copyPath.isEnabled = false
            permission.isEnabled = false
        } else {
            transfer.isEnabled = transferManager.canTransfer(paths)
        }

        popupMenu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
            }

        })

        popupMenu.show(table, e.x, e.y)
    }

    override fun navigateTo(destination: Path): Boolean {
        assertEventDispatchThread()

        if (loading) return false
        if (workdir == destination) return false

        return reload(workdir, destination)
    }

    override fun getHistory(): List<Path> {
        return history.toList()
    }

    override fun canRedo(): Boolean {
        return undoManager.canRedo() && loading.not()
    }

    override fun canUndo(): Boolean {
        return undoManager.canUndo() && loading.not()
    }

    override fun back() {
        if (loading.not() && undoManager.canUndo()) undoManager.undo()
    }

    override fun forward() {
        if (loading.not() && undoManager.canRedo()) undoManager.redo()
    }

    private fun setNewWorkdir(destination: Path) {
        val oldValue = workdir
        workdir = destination
        firePropertyChange("workdir", oldValue, destination)
    }


    private class MyModel() : DefaultTableModel() {
        companion object {
            const val COLUMN_NAME = 0
            const val COLUMN_TYPE = 1
            const val COLUMN_FILE_SIZE = 2
            const val COLUMN_LAST_MODIFIED_TIME = 3
            const val COLUMN_ATTRS = 4
            const val COLUMN_OWNER = 5
        }

        override fun getColumnCount(): Int {
            return 6
        }

        fun getPath(row: Int): Path {
            return super.getValueAt(row, 0) as Path
        }

        fun getAttributes(row: Int): Attributes {
            return super.getValueAt(row, 1) as Attributes
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return Attributes::class.java
        }

        override fun getValueAt(row: Int, column: Int): Any? {
            return getAttributes(row)
        }

        override fun getColumnName(column: Int): String {
            return when (column) {
                COLUMN_NAME -> I18n.getString("termora.transport.table.filename")
                COLUMN_FILE_SIZE -> I18n.getString("termora.transport.table.size")
                COLUMN_TYPE -> I18n.getString("termora.transport.table.type")
                COLUMN_LAST_MODIFIED_TIME -> I18n.getString("termora.transport.table.modified-time")
                COLUMN_ATTRS -> I18n.getString("termora.transport.table.permissions")
                COLUMN_OWNER -> I18n.getString("termora.transport.table.owner")
                else -> StringUtils.EMPTY
            }
        }

        override fun isCellEditable(row: Int, column: Int): Boolean {
            return false
        }

        fun clear() {
            while (rowCount > 0) {
                removeRow(rowCount - 1)
            }
        }
    }

    private class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                for (c in components) {
                    c.setBounds(0, 0, width, height)
                }
            }
        }
    }

    private inner class LoadingPanel : JXPanel() {
        val busyLabel = JXBusyLabel()

        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(50, 0, 0, 0)
            add(busyLabel, BorderLayout.CENTER)
            addMouseListener(object : MouseAdapter() {})
            isVisible = false
        }

    }


    private data class Attributes(
        val name: String,
        val type: String,
        val isDirectory: Boolean,
        val isFile: Boolean,
        val isSymbolicLink: Boolean,
        val fileSize: Long,
        val permissions: Set<PosixFilePermission>,
        val owner: String,
        val lastModifiedTime: Long
    ) {
        companion object {
            fun computeType(isSymbolicLink: Boolean, isDirectory: Boolean, name: String): String {
                if (isSymbolicLink) {
                    return I18n.getString("termora.transport.table.type.symbolic-link")
                } else if (isDirectory) {
                    return I18n.getString("termora.folder")
                }
                if (name == "..") return StringUtils.EMPTY
                return FilenameUtils.getExtension(name)
            }
        }

        val isParent get() = name == ".."
    }

    private inner class MyDefaultTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component? {
            val attributes = value as? Attributes
            if (attributes == null) {
                return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
            }

            var text = when (column) {
                MyModel.COLUMN_NAME -> attributes.name
                MyModel.COLUMN_TYPE -> attributes.type
                MyModel.COLUMN_FILE_SIZE -> formatBytes(attributes.fileSize)
                // @formatter:off
                MyModel.COLUMN_LAST_MODIFIED_TIME -> DateFormatUtils.format(Date(attributes.lastModifiedTime), I18n.getString("termora.date-format"))
                // @formatter:on
                MyModel.COLUMN_ATTRS -> PosixFilePermissions.toString(attributes.permissions)
                MyModel.COLUMN_OWNER -> attributes.owner
                else -> StringUtils.EMPTY
            }

            // 父行只显示名称
            if (attributes.isParent && column != MyModel.COLUMN_NAME) {
                text = StringUtils.EMPTY
            }

            val c = super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
            icon = null

            if (column == MyModel.COLUMN_NAME) {
                if (fileSystem.isWindowsFileSystem()) {
                    val path = model.getPath(sorter.convertRowIndexToModel(row))
                    icon = if (attributes.isParent) {
                        NativeIcons.folderIcon
                    } else {
                        lru.computeIfAbsent(path.absolutePathString()) {
                            FileSystemView.getFileSystemView().getSystemIcon(File(it))
                        }
                    }
                }

                if (SystemInfo.isLinux) {
                    icon = if (attributes.isDirectory) {
                        folderIcon
                    } else {
                        fileIcon
                    }
                }

                if (icon == null) {
                    icon = if (attributes.isDirectory) {
                        NativeIcons.folderIcon
                    } else {
                        NativeIcons.fileIcon
                    }
                }
            }

            return c
        }
    }

    private inner class MyUndoManager : UndoManager() {
        init {
            limit = 128
        }

        private var listeners = emptyArray<ActionListener>()

        override fun undo() {
            super.undo()
            fireActionPerformed("undo")
        }

        override fun redo() {
            super.redo()
            fireActionPerformed("redo")
        }

        override fun undoOrRedo() {
            super.undoOrRedo()
            fireActionPerformed("undoOrRedo")
        }

        override fun undoTo(edit: UndoableEdit?) {
            super.undoTo(edit)
            fireActionPerformed("undoTo")
        }

        override fun redoTo(edit: UndoableEdit?) {
            super.redoTo(edit)
            fireActionPerformed("redoTo")
        }

        override fun addEdit(anEdit: UndoableEdit?): Boolean {
            val c = super.addEdit(anEdit)
            fireActionPerformed("addEdit")
            return c
        }

        fun addActionListener(listener: ActionListener) {
            listeners += listener
        }

        fun removeActionListener(listener: ActionListener) {
            listeners = ArrayUtils.removeElement(listeners, listener)
        }

        private fun fireActionPerformed(command: String) {
            listeners.forEach { it.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, command)) }
        }
    }
}