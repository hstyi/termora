package app.termora.sftp

import app.termora.*
import app.termora.actions.AnActionEvent
import app.termora.actions.SettingsAction
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.util.SystemInfo
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.apache.sshd.sftp.client.fs.SftpPath
import org.apache.sshd.sftp.client.fs.SftpPosixFileAttributes
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.*
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import kotlin.collections.ArrayDeque
import kotlin.io.path.*
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds


@Suppress("DuplicatedCode")
class FileSystemViewTable(
    private val fileSystem: FileSystem,
    private val transportManager: TransportManager,
    private val coroutineScope: CoroutineScope
) : JTable(), Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(FileSystemViewTable::class.java)
    }

    private val sftp get() = Database.getDatabase().sftp
    private val model = FileSystemViewTableModel()
    private val table = this
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val sftpPanel
        get() = SwingUtilities.getAncestorOfClass(SFTPPanel::class.java, this)
                as SFTPPanel
    private val fileSystemViewPanel
        get() = SwingUtilities.getAncestorOfClass(FileSystemViewPanel::class.java, this)
                as FileSystemViewPanel
    private val actionManager get() = ActionManager.getInstance()
    private val isDisposed = AtomicBoolean(false)

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.setModel(model)

        super.getTableHeader().setReorderingAllowed(false)
        super.setDragEnabled(true)
        super.setDropMode(DropMode.ON_OR_INSERT_ROWS)
        super.setCellSelectionEnabled(false)
        super.setRowSelectionAllowed(true)
        super.setRowHeight(UIManager.getInt("Table.rowHeight"))
        super.setAutoResizeMode(AUTO_RESIZE_OFF)
        super.setFillsViewportHeight(true)
        super.putClientProperty(
            FlatClientProperties.STYLE, mapOf(
                "showHorizontalLines" to true,
                "showVerticalLines" to true,
                "cellMargins" to Insets(0, 4, 0, 4),
            )
        )

        setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                foreground = null
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                icon = if (column == FileSystemViewTableModel.COLUMN_NAME) model.getAttr(row).icon else null
                foreground = if (!isSelected && model.getAttr(row).isHidden)
                    UIManager.getColor("textInactiveText") else foreground
                return c
            }
        })

        columnModel.getColumn(FileSystemViewTableModel.COLUMN_NAME).preferredWidth = 250
        columnModel.getColumn(FileSystemViewTableModel.COLUMN_LAST_MODIFIED_TIME).preferredWidth = 130

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

                    showContextMenu(rows, e)
                } else if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    val row = table.selectedRow
                    if (row <= 0 || row >= table.rowCount) return
                    val attr = model.getAttr(row)
                    if (attr.isDirectory) return
                    // 传输
                    transfer(arrayOf(attr))
                }
            }
        })


        // Delete key
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if ((SystemInfo.isMacOS && e.keyCode == KeyEvent.VK_BACK_SPACE) || (e.keyCode == KeyEvent.VK_DELETE)) {
                    val rows = selectedRows
                    if (rows.contains(0)) return
                    val attrs = rows.map { model.getAttr(it) }.toTypedArray()
                    val files = attrs.map { it.path }.toTypedArray()
                    deletePaths(files, false)
                } else if (!SystemInfo.isMacOS && e.keyCode == KeyEvent.VK_F5) {
                    fileSystemViewPanel.reload(true)
                } else if (!SystemInfo.isMacOS && e.keyCode == KeyEvent.VK_F2) {
                    renameSelection()
                }
            }
        })

        table.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                val dropLocation = support.dropLocation as? JTable.DropLocation ?: return false
                // 如果不是新增行，并且光标不在第一列，那么不允许
                if (!dropLocation.isInsertRow && dropLocation.column != FileSystemViewTableModel.COLUMN_NAME) return false
                // 如果不是新增行，如果在一个文件上，那么不允许
                if (!dropLocation.isInsertRow && model.getAttr(dropLocation.row).isFile) return false

                if (support.isDataFlavorSupported(FileSystemTableRowTransferable.dataFlavor)) {
                    val data = support.transferable.getTransferData(FileSystemTableRowTransferable.dataFlavor)
                    return data is FileSystemTableRowTransferable && data.source != table
                } else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return !fileSystem.isLocal()
                }

                return false
            }

            override fun importData(support: TransferSupport): Boolean {
                val dropLocation = support.dropLocation as? JTable.DropLocation ?: return false
                // 如果不是新增行，并且光标不在第一列，那么不允许
                if (!dropLocation.isInsertRow && dropLocation.column != FileSystemViewTableModel.COLUMN_NAME) return false
                // 如果不是新增行，如果在一个文件上，那么不允许
                if (!dropLocation.isInsertRow && model.getAttr(dropLocation.row).isFile) return false

                var targetWorkdir: Path? = null

                // 变更工作目录
                if (!dropLocation.isInsertRow) {
                    targetWorkdir = model.getAttr(dropLocation.row).path
                }

                if (support.isDataFlavorSupported(FileSystemTableRowTransferable.dataFlavor)) {
                    val data = support.transferable.getTransferData(FileSystemTableRowTransferable.dataFlavor)
                    if (data !is FileSystemTableRowTransferable) return false
                    // 委托源表开始传输
                    data.source.transfer(data.attrs.toTypedArray(), false, targetWorkdir)
                    return true
                } else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    val files = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                    if (files.isEmpty()) return false
                    val paths = files.filterIsInstance<File>()
                        .map { FileSystemViewTableModel.Attr(it.toPath()) }
                        .toTypedArray()
                    if (paths.isEmpty()) return false
                    val localTarget = sftpPanel.getLocalTarget()
                    val table = localTarget.getData(SFTPDataProviders.FileSystemViewTable) ?: return false
                    // 委托最左侧的本地文件系统传输
                    table.transfer(paths, true, targetWorkdir)
                    return true
                }
                return false
            }

            override fun getSourceActions(c: JComponent?): Int {
                return COPY
            }

            override fun createTransferable(c: JComponent?): Transferable? {
                val attrs = table.selectedRows.filter { it != 0 }.map { model.getAttr(it) }
                if (attrs.isEmpty()) return null
                return FileSystemTableRowTransferable(table, attrs)
            }
        }

        // 快速导航
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val c = e.keyChar
                val count = model.rowCount
                val row = selectedRow + 1
                for (i in row until count) if (navigate(i, c)) return
                for (i in 0 until count) if (navigate(i, c)) return
            }

            private fun navigate(row: Int, c: Char): Boolean {
                val name = model.getAttr(row).name
                if (name.startsWith(c, true)) {
                    clearSelection()
                    addRowSelectionInterval(row, row)
                    table.scrollRectToVisible(table.getCellRect(row, 0, true))
                    return true
                }
                return false
            }
        })
    }


    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            if (!fileSystem.isSFTP()) {
                coroutineScope.cancel()
            }
        }
    }

    private fun showContextMenu(rows: IntArray, e: MouseEvent) {
        val attrs = rows.map { model.getAttr(it) }.toTypedArray()
        val files = attrs.map { it.path }.toTypedArray()
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
        edit.isEnabled = fileSystem.isSFTP() && attrs.all { it.isFile }
        popupMenu.addSeparator()
        // 复制路径
        val copyPath = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.copy-path"))

        // 如果是本地，那么支持打开本地路径
        if (fileSystem.isLocal()) {
            popupMenu.add(
                I18n.getString(
                    "termora.transport.table.contextmenu.open-in-folder",
                    if (SystemInfo.isMacOS) I18n.getString("termora.finder")
                    else if (SystemInfo.isWindows) I18n.getString("termora.explorer")
                    else I18n.getString("termora.folder")
                )
            ).addActionListener {
                Application.browseInFolder(files.last().toFile())
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
        if (!fileSystem.isSFTP()) {
            rmrf.isVisible = false
        }

        // 修改权限
        val permission = popupMenu.add(I18n.getString("termora.transport.table.contextmenu.change-permissions"))
        permission.isEnabled = false

        // 如果是本地系统文件，那么不允许修改权限，用户应该自己修改
        if (fileSystem.isSFTP() && rows.isNotEmpty()) {
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
                newFolderOrFile(false)
            }
        })
        // 新建文件
        newFile.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                newFolderOrFile(true)
            }
        })
        rename.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                renameSelection()
            }
        })
        delete.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                deletePaths(files, false)
            }
        })
        rmrf.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                deletePaths(files, true)
            }
        })
        copyPath.addActionListener {
            val sb = StringBuilder()
            attrs.forEach { sb.append(it.path.absolutePathString()).appendLine() }
            sb.deleteCharAt(sb.length - 1)
            toolkit.systemClipboard.setContents(StringSelection(sb.toString()), null)
        }
        edit.addActionListener { if (files.isNotEmpty()) editFiles(files) }
        permission.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val last = attrs.last()
                val dialog = PosixFilePermissionDialog(
                    SwingUtilities.getWindowAncestor(table),
                    last.posixFilePermissions
                )
                val permissions = dialog.open() ?: return

                if (fileSystemViewPanel.requestLoading()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val c = runCatching { Files.setPosixFilePermissions(last.path, permissions) }.onFailure {
                            withContext(Dispatchers.Swing) {
                                OptionPane.showMessageDialog(
                                    owner,
                                    ExceptionUtils.getMessage(it),
                                    messageType = JOptionPane.ERROR_MESSAGE
                                )
                            }
                        }

                        // stop loading
                        fileSystemViewPanel.stopLoading()

                        // reload
                        if (c.isSuccess) {
                            fileSystemViewPanel.reload(true)
                        }
                    }
                }
            }
        })
        refresh.addActionListener { fileSystemViewPanel.reload() }
        transfer.addActionListener { transfer(attrs) }

        if (rows.isEmpty() || hasParent) {
            transfer.isEnabled = false
            rename.isEnabled = false
            delete.isEnabled = false
            edit.isEnabled = false
            rmrf.isEnabled = false
            copyPath.isEnabled = false
            permission.isEnabled = false
        } else {
            transfer.isEnabled = sftpPanel.canTransfer(table)
        }


        popupMenu.show(table, e.x, e.y)
    }

    private fun renameSelection() {
        val index = selectedRow
        if (index < 0) return
        val attr = model.getAttr(index)
        val text = OptionPane.showInputDialog(
            owner,
            value = attr.name,
            title = I18n.getString("termora.transport.table.contextmenu.rename")
        ) ?: return
        if (text.isBlank() || text == attr.name) return
        if (model.getPathNames().contains(text)) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.transport.file-already-exists", text),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            return
        }
        fileSystemViewPanel.renameTo(attr.path, attr.path.parent.resolve(text))
    }

    private fun editFiles(files: Array<Path>) {
        if (files.isEmpty()) return

        if (SystemInfo.isLinux) {
            if (sftp.editCommand.isBlank()) {
                OptionPane.showMessageDialog(
                    owner,
                    I18n.getString("termora.transport.table.contextmenu.edit-command"),
                    messageType = JOptionPane.INFORMATION_MESSAGE
                )
                actionManager.getAction(SettingsAction.SETTING)
                    ?.actionPerformed(AnActionEvent(this, StringUtils.EMPTY, EventObject(this)))
                return
            }
        }

        for (file in files) {
            val dir = Application.createSubTemporaryDir()
            val path = Paths.get(dir.absolutePathString(), file.name)

            val newTransport = createTransport(file, false, 0L)
                .apply { target = path }

            transportManager.addTransportListener(object : TransportListener {
                override fun onTransportChanged(transport: Transport) {
                    if (transport != newTransport) return
                    if (transport.status != TransportStatus.Done && transport.status != TransportStatus.Failed) return
                    transportManager.removeTransportListener(this)
                    if (transport.status != TransportStatus.Done) return
                    // 监听文件变动
                    listenFileChange(path, file)
                }
            })

            transportManager.addTransport(newTransport)

        }
    }

    private fun listenFileChange(localPath: Path, remotePath: Path) {
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

        var lastModifiedTime = localPath.getLastModifiedTime().toMillis()

        coroutineScope.launch(Dispatchers.IO) {
            while (coroutineScope.isActive) {
                try {
                    if (isDisposed.get() || !Files.exists(localPath)) break
                    val nowModifiedTime = localPath.getLastModifiedTime().toMillis()
                    if (nowModifiedTime != lastModifiedTime) {
                        lastModifiedTime = nowModifiedTime
                        if (log.isDebugEnabled) {
                            log.debug("Listening to file {} changes", localPath.absolutePathString())
                        }
                        withContext(Dispatchers.Swing) {
                            transportManager.addTransport(
                                createTransport(localPath, false, 0L)
                                    .apply { target = remotePath })
                        }
                    }
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                    break
                }

                delay(500.milliseconds)
            }

        }
    }

    private fun parseCommand(command: String): List<String> {
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

    private fun newFolderOrFile(isFile: Boolean) {
        val name = if (isFile) I18n.getString("termora.transport.table.contextmenu.new.file")
        else I18n.getString("termora.welcome.contextmenu.new.folder.name")
        val text = OptionPane.showInputDialog(owner, title = name, value = name) ?: return
        if (text.isBlank()) return
        if (model.getPathNames().contains(text)) {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.transport.file-already-exists", text),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            return
        }
        fileSystemViewPanel.newFolderOrFile(text, isFile)
    }

    private fun deletePaths(paths: Array<Path>, rm: Boolean = false) {
        if (OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString(if (rm) "termora.transport.table.contextmenu.rm-warning" else "termora.transport.table.contextmenu.delete-warning"),
                messageType = if (rm) JOptionPane.ERROR_MESSAGE else JOptionPane.WARNING_MESSAGE
            ) != JOptionPane.YES_OPTION
        ) {
            return
        }

        if (!fileSystemViewPanel.requestLoading()) {
            return
        }

        coroutineScope.launch {

            runCatching {
                if (fileSystem.isSFTP()) {
                    deleteSftpPaths(paths, rm)
                } else {
                    deleteRecursively(paths)
                }
            }.onFailure {
                if (log.isErrorEnabled) {
                    log.error(it.message, it)
                }
            }

            // 停止加载
            fileSystemViewPanel.stopLoading()

            // 刷新
            fileSystemViewPanel.reload()

        }
    }

    private fun deleteSftpPaths(paths: Array<Path>, rm: Boolean = false) {
        val fs = this.fileSystem as SftpFileSystem
        if (rm) {
            for (path in paths) {
                fs.session.executeRemoteCommand(
                    "rm -rf '${path.absolutePathString()}'",
                    OutputStream.nullOutputStream(),
                    Charsets.UTF_8
                )
            }
        } else {
            fs.client.use {
                for (path in paths) {
                    deleteRecursivelySFTP(path as SftpPath, it)
                }
            }
        }
    }

    private fun deleteRecursively(paths: Array<Path>) {
        for (path in paths) {
            FileUtils.deleteQuietly(path.toFile())
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

    private fun transfer(
        attrs: Array<FileSystemViewTableModel.Attr>,
        fromLocalSystem: Boolean = false,
        targetWorkdir: Path? = null
    ) {

        assertEventDispatchThread()

        val target = sftpPanel.getTarget(table) ?: return
        val table = target.getData(SFTPDataProviders.FileSystemViewTable) ?: return
        var overwriteAll = false

        for (attr in attrs) {

            if (!overwriteAll) {
                val targetAttr = 0.rangeUntil(table.model.rowCount).map { table.model.getAttr(it) }
                    .find { it.name == attr.name }
                if (targetAttr != null) {
                    val askTransfer = askTransfer(attr, targetAttr)
                    if (askTransfer.option != JOptionPane.YES_OPTION) {
                        continue
                    }
                    if (askTransfer.action == AskTransfer.Action.Skip) {
                        if (askTransfer.applyAll) break
                        continue
                    } else if (askTransfer.action == AskTransfer.Action.Overwrite) {
                        overwriteAll = askTransfer.applyAll
                    }
                }
            }

            coroutineScope.launch {
                try {
                    doTransfer(arrayOf(attr), fromLocalSystem, targetWorkdir)
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                }
            }
        }

    }

    private data class AskTransfer(
        val option: Int,
        val action: Action,
        val applyAll: Boolean
    ) {
        enum class Action {
            Overwrite,
            Skip
        }
    }

    private fun askTransfer(
        sourceAttr: FileSystemViewTableModel.Attr,
        targetAttr: FileSystemViewTableModel.Attr
    ): AskTransfer {
        val formMargin = "7dlu"
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow, 2dlu, left:pref",
            "pref, 12dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, 16dlu, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
        )

        val iconSize = 36

        val targetIcon = if (SystemInfo.isWindows)
            NativeFileIcons.getIcon(targetAttr.name, targetAttr.isFile, iconSize, iconSize).first
        else if (targetAttr.isDirectory) {
            FlatSVGIcon(Icons.folder.name, iconSize, iconSize)
        } else {
            FlatSVGIcon(Icons.file.name, iconSize, iconSize)
        }

        val sourceIcon = if (SystemInfo.isWindows)
            NativeFileIcons.getIcon(sourceAttr.name, sourceAttr.isFile, iconSize, iconSize).first
        else if (sourceAttr.isDirectory) {
            FlatSVGIcon(Icons.folder.name, iconSize, iconSize)
        } else {
            FlatSVGIcon(Icons.file.name, iconSize, iconSize)
        }

        val sourceModified = if (sourceAttr.modified > 0) DateFormatUtils.format(
            Date(sourceAttr.modified),
            "yyyy/MM/dd HH:mm"
        ) else "-"

        val targetModified = if (targetAttr.modified > 0) DateFormatUtils.format(
            Date(targetAttr.modified),
            "yyyy/MM/dd HH:mm"
        ) else "-"

        val actionsComBoBox = JComboBox<AskTransfer.Action>()
        actionsComBoBox.addItem(AskTransfer.Action.Overwrite)
        actionsComBoBox.addItem(AskTransfer.Action.Skip)
        actionsComBoBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                var text = value?.toString() ?: StringUtils.EMPTY
                if (value == AskTransfer.Action.Overwrite) {
                    text = I18n.getString("termora.transport.sftp.already-exists.overwrite")
                } else if (value == AskTransfer.Action.Skip) {
                    text = I18n.getString("termora.transport.sftp.already-exists.skip")
                }
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }
        val applyAllCheckbox = JCheckBox(I18n.getString("termora.transport.sftp.already-exists.apply-all"))
        val box = Box.createHorizontalBox()
        box.add(actionsComBoBox)
        box.add(Box.createHorizontalStrut(8))
        box.add(applyAllCheckbox)
        box.add(Box.createHorizontalGlue())

        val ttBox = Box.createVerticalBox()
        ttBox.add(JLabel(I18n.getString("termora.transport.sftp.already-exists.message1")))
        ttBox.add(JLabel(I18n.getString("termora.transport.sftp.already-exists.message2")))

        val warningIcon = FlatSVGIcon(
            Icons.warningIntroduction.name,
            iconSize,
            iconSize
        )

        var rows = 1
        val step = 2
        val panel = FormBuilder.create().layout(layout)
            // tip
            .add(JLabel(warningIcon)).xy(1, rows, "center, fill")
            .add(ttBox).xyw(3, rows, 3).apply { rows += step }
            // name
            .add(JLabel("${I18n.getString("termora.transport.sftp.already-exists.name")}:")).xy(1, rows)
            .add(sourceAttr.name).xyw(3, rows, 3).apply { rows += step }
            // separator
            .addSeparator(StringUtils.EMPTY).xyw(1, rows, 5).apply { rows += step }
            // Destination
            .add("${I18n.getString("termora.transport.sftp.already-exists.destination")}:").xy(1, rows)
            .apply { rows += step }
            // Folder
            .add(JLabel(targetIcon)).xy(1, rows, "center, fill")
            .add(targetModified).xyw(3, rows, 3).apply { rows += step }
            // Source
            .add("${I18n.getString("termora.transport.sftp.already-exists.source")}:").xy(1, rows)
            .apply { rows += step }
            // Folder
            .add(JLabel(sourceIcon)).xy(1, rows, "center, fill")
            .add(sourceModified).xyw(3, rows, 3).apply { rows += step }
            // separator
            .addSeparator(StringUtils.EMPTY).xyw(1, rows, 5).apply { rows += step }
            // name
            .add(JLabel("${I18n.getString("termora.transport.sftp.already-exists.actions")}:")).xy(1, rows)
            .add(box).xyw(3, rows, 3).apply { rows += step }
            .build()
        panel.putClientProperty("SKIP_requestFocusInWindow", true)

        return AskTransfer(
            option = OptionPane.showConfirmDialog(
                owner, panel,
                messageType = JOptionPane.PLAIN_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION,
                title = sourceAttr.name,
                initialValue = JOptionPane.YES_OPTION,
            ) {
                it.size = Dimension(max(UIManager.getInt("Dialog.width") - 220, it.width), it.height)
            },
            action = actionsComBoBox.selectedItem as AskTransfer.Action,
            applyAll = applyAllCheckbox.isSelected
        )

    }


    /**
     * 开始查找所有子，查找到之后立即添加任务，如果添加失败（任意一个）那么立即终止
     */
    private fun doTransfer(
        attrs: Array<FileSystemViewTableModel.Attr>,
        fromLocalSystem: Boolean,
        targetWorkdir: Path?
    ) {
        if (attrs.isEmpty()) return
        val sftpPanel = this.sftpPanel
        val target = sftpPanel.getTarget(table) ?: return
        var isTerminate = false
        val queue = ArrayDeque<Transport>()

        for (attr in attrs) {

            /**
             * 定义一个添加器，它可以自动的判断导入/拖拽行为
             */
            val adder = object {
                fun add(transport: Transport): Boolean {
                    return addTransport(
                        sftpPanel,
                        if (fromLocalSystem) attr.path.parent else null,
                        target,
                        targetWorkdir,
                        transport
                    )
                }
            }

            if (attr.isFile) {
                if (!adder.add(createTransport(attr.path, false, 0).apply { scanned() })) {
                    isTerminate = true
                    break
                }
                continue
            }

            queue.clear()

            try {
                walk(attr.path, object : FileVisitor<Path> {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val transport = createTransport(dir, true, queue.lastOrNull()?.id ?: 0L)
                            .apply { queue.addLast(this) }
                        if (adder.add(transport)) return FileVisitResult.CONTINUE
                        return FileVisitResult.TERMINATE.apply { isTerminate = true }
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (queue.isEmpty()) return FileVisitResult.SKIP_SIBLINGS
                        val transport = createTransport(file, false, queue.last().id).apply { scanned() }
                        if (adder.add(transport)) return FileVisitResult.CONTINUE
                        return FileVisitResult.TERMINATE.apply { isTerminate = true }
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        // 标记为扫描完毕
                        queue.removeLast().scanned()
                        return FileVisitResult.CONTINUE
                    }

                })
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
                isTerminate = true
            }

            if (isTerminate) break
        }

        if (isTerminate) {
            // 把剩余的文件夹标记为扫描完毕
            while (queue.isNotEmpty()) queue.removeLast().scanned()
        }
    }

    private fun walk(dir: Path, visitor: FileVisitor<Path>) {
        if (fileSystem is SftpFileSystem) {
            val attr = SftpPosixFileAttributes(dir, SftpClient.Attributes())
            fileSystem.client.use { walkSFTP(dir, attr, visitor, it) }
        } else {
            Files.walkFileTree(dir, setOf(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, visitor)
        }
    }

    private fun walkSFTP(
        dir: Path,
        attr: SftpPosixFileAttributes,
        visitor: FileVisitor<Path>,
        client: SftpClient
    ): FileVisitResult {

        if (visitor.preVisitDirectory(dir, attr) == FileVisitResult.TERMINATE) {
            return FileVisitResult.TERMINATE
        }

        val paths = client.readDir(dir.absolutePathString())
        for (e in paths) {
            if (e.filename == ".." || e.filename == ".") continue
            if (e.attributes.isDirectory) {
                if (walkSFTP(dir.resolve(e.filename), attr, visitor, client) == FileVisitResult.TERMINATE) {
                    return FileVisitResult.TERMINATE
                }
            } else {
                val result = visitor.visitFile(dir.resolve(e.filename), attr)
                if (result == FileVisitResult.TERMINATE) {
                    return FileVisitResult.TERMINATE
                } else if (result == FileVisitResult.SKIP_SUBTREE) {
                    break
                }
            }
        }

        if (visitor.postVisitDirectory(dir, null) == FileVisitResult.TERMINATE) {
            return FileVisitResult.TERMINATE
        }

        return FileVisitResult.CONTINUE
    }

    private fun addTransport(
        sftpPanel: SFTPPanel,
        sourceWorkdir: Path?,
        target: FileSystemViewPanel,
        targetWorkdir: Path?,
        transport: Transport
    ): Boolean {
        return sftpPanel.addTransport(table, sourceWorkdir, target, targetWorkdir, transport)
    }

    private fun createTransport(source: Path, isDirectory: Boolean, parentId: Long): Transport {
        val transport = Transport(
            source = source,
            target = source,
            parentId = parentId,
            isDirectory = isDirectory,
        )
        if (transport.isFile) {
            transport.filesize.addAndGet(source.fileSize())
        }
        return transport
    }


    private class FileSystemTableRowTransferable(
        val source: FileSystemViewTable,
        val attrs: List<FileSystemViewTableModel.Attr>
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