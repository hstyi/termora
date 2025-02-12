package app.termora.transport

import app.termora.*
import app.termora.actions.AnActionEvent
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.icons.FlatFileViewDirectoryIcon
import com.formdev.flatlaf.icons.FlatFileViewFileIcon
import com.formdev.flatlaf.ui.FlatTableUI
import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.FileUtils
import org.apache.commons.io.file.PathUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.apache.sshd.sftp.client.fs.SftpPath
import org.jdesktop.swingx.JXBusyLabel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.*
import java.text.MessageFormat
import java.util.*
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.time.Duration.Companion.milliseconds


/**
 * 文件系统面板
 */
class FileSystemPanel(
    private val fileSystem: FileSystem,
    private val host: Host
) : JPanel(BorderLayout()), Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(FileSystemPanel::class.java)
    }

    private val tableModel = FileSystemTableModel(fileSystem)
    private val table = JTable(tableModel)
    private val parentBtn = JButton(Icons.up)
    private val workdirTextField = OutlineTextField()
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val layeredPane = FileSystemLayeredPane()
    private val loadingPanel = LoadingPanel()
    private val bookmarkBtn = BookmarkButton()
    private val homeBtn = JButton(Icons.homeFolder)
    private val showHiddenFilesBtn = JButton(Icons.eyeClose)
    private val properties get() = Database.getDatabase().properties
    private val showHiddenFilesKey by lazy { "termora.transport.host.${host.id}.show-hidden-files" }
    private val evt by lazy { AnActionEvent(this, StringUtils.EMPTY, EventObject(this)) }

    /**
     * Edit
     */
    private val coroutineScope by lazy { CoroutineScope(Dispatchers.IO + SupervisorJob()) }

    val workdir get() = tableModel.workdir

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        // 设置书签名称
        bookmarkBtn.name = "Host.${host.id}.Bookmarks"
        bookmarkBtn.isBookmark = bookmarkBtn.getBookmarks().contains(workdir.toString())

        table.setUI(FlatTableUI())
        table.dragEnabled = true
        table.dropMode = DropMode.INSERT_ROWS
        table.rowHeight = UIManager.getInt("Table.rowHeight")
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        table.fillsViewportHeight = true
        table.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
            )
        )

        table.setDefaultRenderer(
            Any::class.java,
            DefaultTableCellRenderer().apply {
                horizontalAlignment = SwingConstants.CENTER
            }
        )

        val modifyDateColumn = table.columnModel.getColumn(FileSystemTableModel.COLUMN_LAST_MODIFIED_TIME)
        modifyDateColumn.preferredWidth = 130

        val nameColumn = table.columnModel.getColumn(FileSystemTableModel.COLUMN_NAME)
        nameColumn.preferredWidth = 250
        nameColumn.setCellRenderer(object : DefaultTableCellRenderer() {
            private val b = BorderFactory.createEmptyBorder(0, 4, 0, 0)
            private val d = FlatFileViewDirectoryIcon()
            private val f = FlatFileViewFileIcon()

            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                var text = value.toString()
                // name
                if (value is FileSystemTableModel.CacheablePath) {
                    text = value.fileName
                    icon = if (value.isDirectory) d else f
                    iconTextGap = 4
                }

                val c = super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
                border = b
                return c
            }
        })

        parentBtn.toolTipText = I18n.getString("termora.transport.parent-folder")
        showHiddenFilesBtn.toolTipText = I18n.getString("termora.transport.show-hidden-files")

        if (properties.getString(showHiddenFilesKey, "true").toBoolean()) {
            showHiddenFilesBtn.icon = Icons.eye
            tableModel.isShowHiddenFiles = true
        } else {
            showHiddenFilesBtn.icon = Icons.eyeClose
            properties.putString(showHiddenFilesKey, "true")
            tableModel.isShowHiddenFiles = false
        }


        val toolbar = FlatToolBar()
        toolbar.add(homeBtn)
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(workdirTextField)
        toolbar.add(bookmarkBtn)
        toolbar.add(showHiddenFilesBtn)
        toolbar.add(parentBtn)
        toolbar.add(JButton(Icons.refresh).apply {
            addActionListener { reload() }
            toolTipText = I18n.getString("termora.transport.table.contextmenu.refresh")
        })
        toolbar.border = BorderFactory.createEmptyBorder(4, 2, 4, 2)

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(loadingPanel, JLayeredPane.MODAL_LAYER as Any)

        add(toolbar, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)

    }

    private fun initEvents() {

        homeBtn.addActionListener {
            if (tableModel.isLocalFileSystem) {
                tableModel.workdir(SystemUtils.USER_HOME)
            } else if (fileSystem is SftpFileSystem) {
                tableModel.workdir(fileSystem.defaultDir)
            }
            reload()
        }

        bookmarkBtn.addActionListener { e ->
            if (e.actionCommand.isNullOrBlank()) {
                if (bookmarkBtn.isBookmark) {
                    bookmarkBtn.deleteBookmark(workdir.toString())
                } else {
                    bookmarkBtn.addBookmark(workdir.toString())
                }
                bookmarkBtn.isBookmark = !bookmarkBtn.isBookmark
            } else if (!loadingPanel.isLoading) {
                tableModel.workdir(e.actionCommand)
                reload()
            }
        }

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

                    showContextMenu(rows.filter { it != 0 }.toIntArray(), e)
                }
            }
        })


        // double click
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val row = table.selectedRow
                    if (row < 0) return
                    val path = tableModel.getCacheablePath(row)
                    if (path.isDirectory) {
                        openFolder()
                    } else {
                        transport(listOf(path))
                    }
                }
            }
        })


        table.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                if (support.isDataFlavorSupported(FileSystemTableRowTransferable.dataFlavor)) {
                    val data = support.transferable.getTransferData(FileSystemTableRowTransferable.dataFlavor)
                    return data is FileSystemTableRowTransferable && data.fileSystemPanel != this@FileSystemPanel
                } else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return !tableModel.isLocalFileSystem
                }
                return false
            }

            override fun importData(comp: JComponent?, t: Transferable): Boolean {
                if (t.isDataFlavorSupported(FileSystemTableRowTransferable.dataFlavor)) {
                    val data = t.getTransferData(FileSystemTableRowTransferable.dataFlavor)
                    if (data !is FileSystemTableRowTransferable) {
                        return false
                    }
                    data.fileSystemPanel.transport(data.paths)
                    return true
                } else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                    if (files.isEmpty()) return false
                    copyLocalFileToFileSystem(files.filterIsInstance<File>())
                    return true
                }
                return false
            }

            override fun getSourceActions(c: JComponent?): Int {
                return COPY
            }

            override fun createTransferable(c: JComponent?): Transferable? {
                val paths = table.selectedRows.filter { it != 0 }.map { tableModel.getCacheablePath(it) }
                if (paths.isEmpty()) {
                    return null
                }
                return FileSystemTableRowTransferable(this@FileSystemPanel, paths)
            }
        }

        // 工作目录变动
        tableModel.addPropertyChangeListener {
            if (it.propertyName == "workdir") {
                workdirTextField.text = tableModel.workdir.toAbsolutePath().toString()
                bookmarkBtn.isBookmark = bookmarkBtn.getBookmarks().contains(workdirTextField.text)
            }
        }

        // 修改工作目录
        workdirTextField.addActionListener {
            val text = workdirTextField.text
            if (text.isBlank()) {
                workdirTextField.text = tableModel.workdir.toAbsolutePath().toString()
                reload()
            } else {
                val path = fileSystem.getPath(workdirTextField.text)
                if (Files.exists(path)) {
                    tableModel.workdir(path)
                    reload()
                } else {
                    workdirTextField.outline = "error"
                }
            }
        }

        // 返回上一级目录
        parentBtn.addActionListener {
            if (tableModel.rowCount > 0) {
                val path = tableModel.getCacheablePath(0)
                if (path.isDirectory && path.fileName == "..") {
                    tableModel.workdir(path.path)
                    reload()
                }
            }
        }

        // 显示隐藏文件
        showHiddenFilesBtn.addActionListener {
            val showHiddenFiles = tableModel.isShowHiddenFiles
            tableModel.isShowHiddenFiles = !showHiddenFiles
            if (tableModel.isShowHiddenFiles) {
                showHiddenFilesBtn.icon = Icons.eye
            } else {
                showHiddenFilesBtn.icon = Icons.eyeClose
            }
        }

        // 如果不是本地的文件系统，那么支持粘贴
        if (!tableModel.isLocalFileSystem) {
            table.actionMap.put("paste", object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    if (!toolkit.systemClipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                        return
                    }
                    val files = (toolkit.systemClipboard.getData(DataFlavor.javaFileListFlavor) ?: return) as List<*>
                    copyLocalFileToFileSystem(files.filterIsInstance<File>())
                }
            })
        }

        Disposer.register(this, object : Disposable {
            override fun dispose() {
                properties.putString(showHiddenFilesKey, "${tableModel.isShowHiddenFiles}")
            }
        })

    }

    override fun dispose() {
        coroutineScope.cancel()
    }

    private fun copyLocalFileToFileSystem(files: List<File>) {
        val event = AnActionEvent(this, StringUtils.EMPTY, EventObject(this))
        val transportPanel = event.getData(TransportDataProviders.TransportPanel) ?: return
        val leftFileSystemTabbed = event.getData(TransportDataProviders.LeftFileSystemTabbed) ?: return
        val localFileSystemPanel = leftFileSystemTabbed.getFileSystemPanel(0) ?: return

        val paths = files.map { FileSystemTableModel.CacheablePath(it.toPath()) }
        for (path in paths) {
            if (path.isDirectory) {
                Files.walk(path.path).use {
                    for (e in it) {
                        transportPanel.transport(
                            sourceWorkdir = path.path.parent,
                            targetWorkdir = workdir,
                            isSourceDirectory = e.isDirectory(),
                            sourcePath = e,
                            sourceHolder = localFileSystemPanel,
                            targetHolder = this@FileSystemPanel
                        )
                    }
                }
            } else {
                transportPanel.transport(
                    sourceWorkdir = path.path.parent,
                    targetWorkdir = workdir,
                    isSourceDirectory = false,
                    sourcePath = path.path,
                    sourceHolder = localFileSystemPanel,
                    targetHolder = this@FileSystemPanel
                )
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun reload() {
        if (loadingPanel.isLoading) {
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            runCatching { suspendReload() }
        }
    }

    private suspend fun suspendReload() {
        if (loadingPanel.isLoading) {
            return
        }

        withContext(Dispatchers.Swing) {
            // reload
            loadingPanel.start()
            workdirTextField.text = workdir.toString()
        }

        try {
            tableModel.reload()
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(
                    owner,
                    ExceptionUtils.getRootCauseMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
            return
        } finally {
            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }
        }

        withContext(Dispatchers.Swing) {
            table.scrollRectToVisible(table.getCellRect(0, 0, true))
        }
    }


    private fun openFolder() {
        val row = table.selectedRow
        if (row < 0) return
        val path = tableModel.getCacheablePath(row)
        if (path.isDirectory) {
            tableModel.workdir(path.path)
            reload()
        }
    }

    private fun canTransfer(): Boolean {
        val event = AnActionEvent(this, StringUtils.EMPTY, EventObject(this))
        val leftFileSystemTabbed = event.getData(TransportDataProviders.LeftFileSystemTabbed) ?: return false
        val rightFileSystemTabbed = event.getData(TransportDataProviders.RightFileSystemTabbed) ?: return false

        val parent = SwingUtilities.getAncestorOfClass(FileSystemTabbed::class.java, this)
        if (parent == leftFileSystemTabbed) {
            return event.getData(TransportDataProviders.RightFileSystemPanel) != null
        } else if (parent == rightFileSystemTabbed) {
            return event.getData(TransportDataProviders.LeftFileSystemPanel) != null
        }

        return false
    }


    private fun showContextMenu(rows: IntArray, event: MouseEvent) {
        val paths = rows.filter { it != 0 }.map { tableModel.getCacheablePath(it) }
        val popupMenu = FlatPopupMenu()
        val newMenu = JMenu(I18n.getString("termora.transport.table.contextmenu.new"))

        // 创建文件夹
        newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.folder")).addActionListener {
            newFolderOrFile(file = false)
        }

        // 创建文件
        newMenu.add(I18n.getString("termora.transport.table.contextmenu.new.file")).addActionListener {
            newFolderOrFile(file = true)
        }


        // 传输
        val transfer = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.transfer"))
        transfer.addActionListener {
            if (paths.isNotEmpty()) {
                transport(paths)
            }
        }

        // 编辑
        val edit = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.edit"))
        // 不是本地文件系统 & 包含文件
        edit.isEnabled = !tableModel.isLocalFileSystem && paths.any { !it.isDirectory }
        edit.addActionListener {
            val files = paths.filter { !it.isDirectory }
            if (files.isNotEmpty()) {
                editFiles(files)
            }
        }

        popupMenu.addSeparator()

        // 复制路径
        val copyPath = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.copy-path"))
        copyPath.addActionListener {
            val row = table.selectedRow
            if (row > 0) {
                toolkit.systemClipboard.setContents(
                    StringSelection(
                        tableModel.getPath(row).toAbsolutePath().toString()
                    ), null
                )
            }
        }

        // 如果是本地，那么支持打开本地路径
        if (tableModel.isLocalFileSystem) {
            if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                popupMenu.add(
                    I18n.getString(
                        "termora.transport.table.contextmenu.open-in-folder",
                        if (SystemInfo.isMacOS) I18n.getString("termora.finder")
                        else if (SystemInfo.isWindows) I18n.getString("termora.explorer")
                        else I18n.getString("termora.folder")
                    )
                ).addActionListener {
                    val row = table.selectedRow
                    if (row > 0) {
                        Desktop.getDesktop().browseFileDirectory(tableModel.getPath(row).toFile())
                    }
                }
            }

        }
        popupMenu.addSeparator()

        // 重命名
        val rename = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.rename"))
        rename.addActionListener { renamePath(tableModel.getPath(rows.last())) }

        // 删除
        val delete = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.delete")).apply {
            addActionListener { deletePaths(rows) }
        }

        // rm -rf
        val rmrf = popupMenu.add(JMenuItem("rm -rf", Icons.errorIntroduction)).apply {
            addActionListener {
                deletePaths(rows, true)
            }
        }

        // 只有 SFTP 可以
        if (fileSystem !is SftpFileSystem) {
            rmrf.isVisible = false
        }

        // 修改权限
        val permission = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.change-permissions"))
        permission.isEnabled = false

        // 如果是本地系统文件，那么不允许修改权限，用户应该自己修改
        if (!tableModel.isLocalFileSystem && rows.isNotEmpty()) {
            permission.isEnabled = true
            permission.addActionListener { changePermissions(tableModel.getCacheablePath(rows.last())) }
        }
        popupMenu.addSeparator()

        // 刷新
        popupMenu.add(I18n.getString("termora.transport.table.contextmenu.refresh"))
            .apply { addActionListener { reload() } }
        popupMenu.addSeparator()

        // 新建
        popupMenu.add(newMenu)


        if (rows.isEmpty()) {
            transfer.isEnabled = false
            rename.isEnabled = false
            delete.isEnabled = false
            rmrf.isEnabled = false
            copyPath.isEnabled = false
            permission.isEnabled = false
        } else {
            transfer.isEnabled = canTransfer()
        }


        popupMenu.show(table, event.x, event.y)
    }

    private fun editFiles(files: List<FileSystemTableModel.CacheablePath>) {
        if (files.isEmpty()) return
        val transportManager = evt.getData(TransportDataProviders.TransportManager) ?: return

        val temporary = Paths.get(Application.getBaseDataDir().absolutePath, "temporary")
        Files.createDirectories(temporary)

        for (file in files) {
            val dir = Files.createTempDirectory(temporary, "termora-")
            val path = Paths.get(dir.absolutePathString(), file.fileName)
            transportManager.addTransport(
                transport = FileTransport(
                    name = file.fileName,
                    source = file.path,
                    target = path,
                    sourceHolder = this,
                    targetHolder = this,
                    listener = editFileTransportListener(file.path, path)
                )
            )
        }
    }

    private fun editFileTransportListener(source: Path, localPath: Path): TransportListener {
        return object : TransportListener {
            private val sftp get() = Database.getDatabase().sftp
            override fun onTransportChanged(transport: Transport) {
                // 传输成功
                if (transport.state == TransportState.Done) {
                    val transportManager = evt.getData(TransportDataProviders.TransportManager) ?: return
                    var lastModifiedTime = localPath.getLastModifiedTime().toMillis()

                    try {
                        if (sftp.editCommand.isNotBlank()) {
                            ProcessBuilder(
                                parseCommand(
                                    MessageFormat.format(
                                        sftp.editCommand,
                                        localPath.absolutePathString()
                                    )
                                )
                            ).start()
                        } else if (SystemInfo.isMacOS) {
                            ProcessBuilder("open", "-a", "TextEdit", localPath.absolutePathString()).start()
                        } else if (SystemInfo.isWindows) {
                            ProcessBuilder("notepad", localPath.absolutePathString()).start()
                        } else {
                            return
                        }
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                        return
                    }


                    coroutineScope.launch(Dispatchers.IO) {
                        while (coroutineScope.isActive) {
                            try {
                                val nowModifiedTime = localPath.getLastModifiedTime().toMillis()
                                if (nowModifiedTime != lastModifiedTime) {
                                    lastModifiedTime = nowModifiedTime
                                    // upload
                                    transportManager.addTransport(
                                        transport = FileTransport(
                                            name = PathUtils.getFileNameString(localPath.fileName),
                                            source = localPath,
                                            target = source,
                                            sourceHolder = this@FileSystemPanel,
                                            targetHolder = this@FileSystemPanel,
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                if (log.isErrorEnabled) {
                                    log.error(e.message, e)
                                }
                                break
                            }
                            delay(250.milliseconds)
                        }
                    }
                }
            }

            fun parseCommand(command: String): List<String> {
                val result = mutableListOf<String>()
                val matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(command)

                while (matcher.find()) {
                    if (matcher.group(1) != null) {
                        result.add(matcher.group(1)) // 处理双引号部分
                    } else {
                        result.add(matcher.group(2).replace("\\\\ ", " "))
                    }
                }
                return result
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun renamePath(path: Path) {
        val fileName = path.fileName.toString()
        val text = InputDialog(
            owner = owner,
            title = fileName,
            text = fileName,
        ).getText() ?: return

        if (fileName == text) return

        loadingPanel.stop()

        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                Files.move(path, path.parent.resolve(text), StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner, it.message ?: ExceptionUtils.getRootCauseMessage(it),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }

            if (result.isSuccess) {
                reload()
            }
        }

    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun newFolderOrFile(file: Boolean = false) {
        val title = I18n.getString("termora.transport.table.contextmenu.new.${if (file) "file" else "folder"}")
        val text = InputDialog(
            owner = owner,
            title = title,
        ).getText() ?: return

        if (text.isEmpty()) return

        loadingPanel.stop()

        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val path = workdir.resolve(text)
                if (path.exists()) {
                    throw IllegalStateException(I18n.getString("termora.transport.file-already-exists", text))
                }
                if (file)
                    Files.createFile(path)
                else
                    Files.createDirectories(path)
            }.onFailure {
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        owner, it.message ?: ExceptionUtils.getRootCauseMessage(it),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }

            if (result.isSuccess) {
                reload()
            }
        }

    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun deletePaths(rows: IntArray, rm: Boolean = false) {
        if (OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString(if (rm) "termora.transport.table.contextmenu.rm-warning" else "termora.transport.table.contextmenu.delete-warning"),
                messageType = if (rm) JOptionPane.ERROR_MESSAGE else JOptionPane.WARNING_MESSAGE
            ) != JOptionPane.YES_OPTION
        ) {
            return
        }

        loadingPanel.start()

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                for (row in rows.sortedDescending()) {
                    try {
                        deleteRecursively(tableModel.getPath(row), rm)
                        withContext(Dispatchers.Swing) {
                            tableModel.removeRow(row)
                        }
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            }.onFailure {
                if (log.isErrorEnabled) {
                    log.error(it.message, it)
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }
        }
    }

    private fun deleteRecursively(path: Path, rm: Boolean) {
        if (path.fileSystem == FileSystems.getDefault()) {
            FileUtils.deleteQuietly(path.toFile())
        } else if (path.fileSystem is SftpFileSystem) {
            val fs = path.fileSystem as SftpFileSystem
            if (rm) {
                fs.session.executeRemoteCommand("rm -rf '$path'")
            } else {
                fs.client.use {
                    deleteRecursivelySFTP(path as SftpPath, it)
                }
            }
        }
    }

    /**
     * 优化删除效率，采用一个连接
     */
    private fun deleteRecursivelySFTP(path: SftpPath, sftpClient: SftpClient) {
        val isDirectory = if (path.attributes != null) path.attributes.isDirectory else path.isDirectory()
        if (isDirectory) {
            for (e in sftpClient.readDir(path.toString())) {
                if (e.filename == ".." || e.filename == ".") {
                    continue
                }
                if (e.attributes.isDirectory) {
                    deleteRecursivelySFTP(path.resolve(e.filename), sftpClient)
                } else {
                    sftpClient.remove(path.resolve(e.filename).toString())
                }
            }
            sftpClient.rmdir(path.toString())
        } else {
            sftpClient.remove(path.toString())
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun changePermissions(cacheablePath: FileSystemTableModel.CacheablePath) {
        val dialog = PosixFilePermissionDialog(
            SwingUtilities.getWindowAncestor(this),
            cacheablePath.posixFilePermissions
        )
        val permissions = dialog.open() ?: return

        loadingPanel.start()

        GlobalScope.launch(Dispatchers.IO) {
            val result = runCatching {
                Files.setPosixFilePermissions(cacheablePath.path, permissions)
            }

            result.onFailure {
                if (log.isErrorEnabled) {
                    log.error(it.message, it)
                }
                withContext(Dispatchers.Swing) {
                    OptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this@FileSystemPanel), ExceptionUtils.getRootCauseMessage(it),
                        messageType = JOptionPane.ERROR_MESSAGE
                    )
                }
            }

            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }

            if (result.isSuccess) {
                reload()
            }


        }
    }

    private fun transport(paths: List<FileSystemTableModel.CacheablePath>) {
        assertEventDispatchThread()
        if (!canTransfer()) {
            return
        }

        loadingPanel.start()
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { doTransport(paths) }
            withContext(Dispatchers.Swing) {
                loadingPanel.stop()
            }
        }
    }

    private suspend fun doTransport(paths: List<FileSystemTableModel.CacheablePath>) {
        if (paths.isEmpty()) return
        val transportPanel = evt.getData(TransportDataProviders.TransportPanel) ?: return
        val leftFileSystemPanel = evt.getData(TransportDataProviders.LeftFileSystemPanel) ?: return
        val rightFileSystemPanel = evt.getData(TransportDataProviders.RightFileSystemPanel) ?: return
        val sourceFileSystemPanel = this
        val targetFileSystemPanel = if (this == leftFileSystemPanel) rightFileSystemPanel else leftFileSystemPanel

        // 收集数据
        for (e in paths) {

            if (!e.isDirectory) {
                val job = TransportJob(
                    fileSystemPanel = this,
                    workdir = workdir,
                    isDirectory = false,
                    path = e.path,
                )
                withContext(Dispatchers.Swing) {
                    transportPanel.transport(
                        sourceWorkdir = workdir,
                        targetWorkdir = targetFileSystemPanel.workdir,
                        isSourceDirectory = false,
                        sourcePath = e.path,
                        sourceHolder = sourceFileSystemPanel,
                        targetHolder = targetFileSystemPanel
                    )
                }
                continue
            }

            withContext(Dispatchers.IO) {
                Files.walk(e.path).use { walkPaths ->
                    for (path in walkPaths) {
                        if (path is SftpPath) {
                            val isDirectory = if (path.attributes != null)
                                path.attributes.isDirectory else path.isDirectory()
                            withContext(Dispatchers.Swing) {
                                transportPanel.transport(
                                    sourceWorkdir = workdir,
                                    targetWorkdir = targetFileSystemPanel.workdir,
                                    isSourceDirectory = isDirectory,
                                    sourcePath = path,
                                    sourceHolder = sourceFileSystemPanel,
                                    targetHolder = targetFileSystemPanel
                                )
                            }
                        } else {
                            val isDirectory = path.isDirectory()
                            withContext(Dispatchers.Swing) {
                                transportPanel.transport(
                                    sourceWorkdir = workdir,
                                    targetWorkdir = targetFileSystemPanel.workdir,
                                    isSourceDirectory = isDirectory,
                                    sourcePath = path,
                                    sourceHolder = sourceFileSystemPanel,
                                    targetHolder = targetFileSystemPanel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private class LoadingPanel : JPanel() {
        private val busyLabel = JXBusyLabel()

        val isLoading get() = busyLabel.isBusy

        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(50, 0, 0, 0)

            add(busyLabel, BorderLayout.CENTER)
            addMouseListener(object : MouseAdapter() {})
            isVisible = false
        }

        fun start() {
            busyLabel.isBusy = true
            isVisible = true
        }

        fun stop() {
            busyLabel.isBusy = false
            isVisible = false
        }
    }

    private class FileSystemLayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                val w = width
                val h = height
                for (c in components) {
                    if (c is JScrollPane) {
                        c.setBounds(0, 0, w, h)
                    } else if (c is LoadingPanel) {
                        c.setBounds(0, 0, w, h)
                    }
                }
            }
        }
    }


    private class FileSystemTableRowTransferable(
        val fileSystemPanel: FileSystemPanel,
        val paths: List<FileSystemTableModel.CacheablePath>
    ) : Transferable {
        companion object {
            val dataFlavor = DataFlavor(FileSystemTableRowTransferable::class.java, "TableRowTransferable")
        }

        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return arrayOf(dataFlavor)
        }

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
            return flavor == dataFlavor
        }

        override fun getTransferData(flavor: DataFlavor?): Any {
            if (flavor != dataFlavor) {
                throw UnsupportedFlavorException(flavor)
            }
            return this
        }

    }
}