package app.termora.sftp

import app.termora.*
import app.termora.actions.DataProvider
import app.termora.terminal.DataKey
import com.formdev.flatlaf.extras.components.FlatToolBar
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import org.jdesktop.swingx.JXBusyLabel
import java.awt.BorderLayout
import java.awt.event.*
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

class FileSystemViewPanel(
    val host: Host,
    val fileSystem: FileSystem,
    private val transportManager: TransportManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : JPanel(BorderLayout()), Disposable, DataProvider {

    private val properties get() = Database.getDatabase().properties
    private val sftp get() = Database.getDatabase().sftp
    private val table = FileSystemViewTable(fileSystem, transportManager, coroutineScope)
    private val disposed = AtomicBoolean(false)
    private var nextReloadTicks = emptyArray<Consumer<Unit>>()
    private val isLoading = AtomicBoolean(false)
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val loadingPanel = LoadingPanel()
    private val layeredPane = LayeredPane()
    private val homeDirectory = getHomeDirectory()
    private val nav = FileSystemViewNav(fileSystem, homeDirectory)
    private var workdir = homeDirectory
    private val model get() = table.model as FileSystemViewTableModel
    private val showHiddenFilesKey = "termora.transport.host.${host.id}.show-hidden-files"
    private var useFileHiding: Boolean
        get() = properties.getString(showHiddenFilesKey, "true").toBoolean()
        set(value) = properties.putString(showHiddenFilesKey, value.toString())

    val isDisposed get() = disposed.get()

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {

        val toolbar = FlatToolBar()
        toolbar.add(createHomeFolderButton())
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(nav)
        toolbar.add(createBookmarkButton())
        toolbar.add(createParentFolderButton())
        toolbar.add(createHiddenFilesButton())
        toolbar.add(createRefreshButton())

        add(toolbar, BorderLayout.NORTH)
        add(layeredPane, BorderLayout.CENTER)

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(loadingPanel, JLayeredPane.PALETTE_LAYER as Any)
    }

    private fun initEvents() {

        Disposer.register(this, table)

        nav.addActionListener { reload() }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount % 2 == 0) {
                    enterTableSelectionFolder()
                }
            }
        })

        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    enterTableSelectionFolder()
                }
            }
        })

        val listener = object : TransportListener, Disposable {
            override fun onTransportChanged(transport: Transport) {
                val path = transport.target.parent ?: return
                if (path.fileSystem != fileSystem) return
                if (path.absolutePathString() != workdir.absolutePathString()) return
                // 立即刷新
                reload(true)
            }

            override fun dispose() {
                transportManager.removeTransportListener(this)
            }
        }
        transportManager.addTransportListener(listener)
        Disposer.register(this, listener)

        // 变更工作目录
        if (SwingUtilities.isEventDispatchThread()) {
            changeWorkdir(homeDirectory)
        } else {
            SwingUtilities.invokeLater { changeWorkdir(homeDirectory) }
        }

    }

    private fun enterTableSelectionFolder(row: Int = table.selectedRow) {
        if (row < 0 || isLoading.get()) return
        val attr = model.getAttr(row)
        if (attr.isFile) return

        // 当前工作目录
        val workdir = getWorkdir()

        // 返回上级之后，选中上级目录
        if (attr.name == "..") {
            val workdirName = workdir.name
            nextReloadTickSelection(workdirName)
        }

        changeWorkdir(attr.path)

    }

    private fun createRefreshButton(): JButton {
        val button = JButton(Icons.refresh)
        button.addActionListener { reload(true) }
        return button
    }

    private fun createHiddenFilesButton(): JButton {
        val button = JButton(if (useFileHiding) Icons.eyeClose else Icons.eye)
        button.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                useFileHiding = !useFileHiding
                button.icon = if (useFileHiding) Icons.eyeClose else Icons.eye
                reload(true)
            }
        })
        return button
    }

    private fun createHomeFolderButton(): JButton {
        val button = JButton(Icons.homeFolder)
        button.addActionListener { nav.changeSelectedPath(homeDirectory) }
        return button
    }

    private fun createBookmarkButton(): JButton {
        val bookmarkBtn = BookmarkButton()
        bookmarkBtn.name = "Host.${host.id}.Bookmarks"
        bookmarkBtn.addActionListener { e ->
            if (e.actionCommand.isNullOrBlank()) {
                if (bookmarkBtn.isBookmark) {
                    bookmarkBtn.deleteBookmark(workdir.toString())
                } else {
                    bookmarkBtn.addBookmark(workdir.toString())
                }
                bookmarkBtn.isBookmark = !bookmarkBtn.isBookmark
            } else {
                changeWorkdir(fileSystem.getPath(e.actionCommand))
            }
        }

        nav.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                bookmarkBtn.isBookmark = bookmarkBtn.getBookmarks().contains(nav.getSelectedPath().absolutePathString())
            }
        })

        return bookmarkBtn
    }


    private fun createParentFolderButton(): AbstractButton {
        val button = JButton(Icons.up)
        button.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (model.rowCount < 1) return
                val attr = model.getAttr(0)
                if (attr !is FileSystemViewTableModel.ParentAttr) return
                enterTableSelectionFolder(0)
            }
        })

        addPropertyChangeListener("workdir") {
            button.isEnabled = model.rowCount > 0 && model.getAttr(0) is FileSystemViewTableModel.ParentAttr
        }

        return button
    }

    private fun nextReloadTickSelection(name: String, consumer: Consumer<Int> = Consumer { }) {
        // 创建成功之后需要修改和选中
        registerNextReloadTick {
            for (i in 0 until table.rowCount) {
                if (model.getAttr(i).name == name) {
                    table.addRowSelectionInterval(i, i)
                    table.scrollRectToVisible(table.getCellRect(i, 0, true))
                    consumer.accept(i)
                    break
                }
            }
        }
    }

    private fun changeWorkdir(workdir: Path) {
        assertEventDispatchThread()
        nav.changeSelectedPath(workdir)
    }

    fun renameTo(oldPath: Path, newPath: Path) {

        // 新建文件夹
        coroutineScope.launch {
            if (requestLoading()) {
                try {
                    Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: Exception) {
                    withContext(Dispatchers.Swing) {
                        OptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(owner),
                            ExceptionUtils.getMessage(e),
                            messageType = JOptionPane.ERROR_MESSAGE
                        )
                    }
                } finally {
                    stopLoading()
                }
            }

            // 创建成功之后需要选中
            nextReloadTickSelection(newPath.name)

            // 立即刷新
            reload()
        }
    }

    fun newFolderOrFile(name: String, isFile: Boolean) {
        coroutineScope.launch {
            if (requestLoading()) {
                try {
                    doNewFolderOrFile(getWorkdir().resolve(name), isFile)
                } finally {
                    stopLoading()
                }
            }

            // 创建成功之后需要修改和选中
            nextReloadTickSelection(name)

            // 立即刷新
            reload()
        }
    }


    private suspend fun doNewFolderOrFile(path: Path, isFile: Boolean) {

        if (Files.exists(path)) {
            withContext(Dispatchers.Swing) {
                OptionPane.showMessageDialog(
                    owner,
                    I18n.getString("termora.transport.file-already-exists", path.name),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
            return
        }

        // 创建文件夹
        withContext(Dispatchers.IO) {
            runCatching { if (isFile) Files.createFile(path) else Files.createDirectories(path) }.onFailure {
                withContext(Dispatchers.Swing) {
                    if (it is Exception) {
                        OptionPane.showMessageDialog(
                            owner,
                            ExceptionUtils.getMessage(it),
                            messageType = JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        }


    }


    fun requestLoading(): Boolean {
        if (isLoading.compareAndSet(false, true)) {
            if (SwingUtilities.isEventDispatchThread()) {
                loadingPanel.start()
            } else {
                SwingUtilities.invokeLater { loadingPanel.start() }
            }
            return true
        }
        return false
    }

    fun stopLoading() {
        if (isLoading.compareAndSet(true, false)) {
            if (SwingUtilities.isEventDispatchThread()) {
                loadingPanel.stop()
            } else {
                SwingUtilities.invokeLater { loadingPanel.stop() }
            }
        }
    }

    fun reload(rememberSelection: Boolean = false) {
        if (!requestLoading()) return
        if (fileSystem.isSFTP()) loadingPanel.start()
        val oldWorkdir = workdir
        val path = nav.getSelectedPath()

        coroutineScope.launch {
            try {

                if (rememberSelection) {
                    withContext(Dispatchers.Swing) {
                        table.selectedRows.sortedDescending().map { model.getAttr(it).name }
                            .forEach { nextReloadTickSelection(it) }
                    }
                }

                runCatching { model.reload(path, useFileHiding) }.onFailure {
                    if (it is Exception) {
                        withContext(Dispatchers.Swing) {
                            OptionPane.showMessageDialog(
                                owner, ExceptionUtils.getMessage(it),
                                messageType = JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }.onSuccess {
                    withContext(Dispatchers.Swing) {
                        workdir = path
                        // 触发工作目录变动
                        firePropertyChange("workdir", oldWorkdir, workdir)
                    }
                }

                withContext(Dispatchers.Swing) {
                    // 触发
                    triggerNextReloadTicks()
                }

            } finally {
                stopLoading()
                if (fileSystem.isSFTP()) {
                    withContext(Dispatchers.Swing) { loadingPanel.stop() }
                }
            }
        }
    }

    private fun getHomeDirectory(): Path {
        if (fileSystem.isSFTP()) {
            val fs = fileSystem as SftpFileSystem
            val host = fs.session.getAttribute(SshClients.HOST_KEY) ?: return fs.defaultDir
            val defaultDirectory = host.options.sftpDefaultDirectory
            if (defaultDirectory.isNotBlank()) {
                return runCatching { fs.getPath(defaultDirectory) }
                    .getOrElse { fs.defaultDir }
            }
            return fs.defaultDir
        }

        if (sftp.defaultDirectory.isNotBlank()) {
            return runCatching { fileSystem.getPath(sftp.defaultDirectory) }
                .getOrElse { fileSystem.getPath(SystemUtils.USER_HOME) }
        }

        return fileSystem.getPath(SystemUtils.USER_HOME)
    }

    fun getWorkdir(): Path {
        return workdir
    }

    private fun registerNextReloadTick(consumer: Consumer<Unit>) {
        nextReloadTicks += Consumer<Unit> { t ->
            assertEventDispatchThread()
            consumer.accept(t)
        }
    }

    private fun triggerNextReloadTicks() {
        for (nextReloadTick in nextReloadTicks) {
            nextReloadTick.accept(Unit)
        }
        nextReloadTicks = emptyArray()
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            val rootChildren = transportManager.getTransports(0L)
            for (child in rootChildren) {
                if (child.source.fileSystem == fileSystem ||
                    child.target.fileSystem == fileSystem
                ) {
                    child.changeStatus(TransportStatus.Failed)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return if (dataKey == SFTPDataProviders.FileSystemViewTable) table as T else null
    }

    private class LoadingPanel : JPanel() {
        private val busyLabel = JXBusyLabel()

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

    private class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                val w = width
                val h = height
                for (c in components) {
                    c.setBounds(0, 0, w, h)
                }
            }
        }
    }

}