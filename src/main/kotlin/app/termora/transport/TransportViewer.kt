package app.termora.transport

import app.termora.Disposable
import app.termora.Disposer
import app.termora.DynamicColor
import app.termora.actions.DataProvider
import app.termora.transport.InternalTransferManager.TransferMode
import kotlinx.coroutines.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import kotlin.io.path.name
import kotlin.io.path.pathString


class TransportViewer : JPanel(BorderLayout()), DataProvider, Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(TransportViewer::class.java)
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val splitPane = JSplitPane()
    private val transferManager = TransferTableModel(coroutineScope)
    private val transferTable = TransferTable(coroutineScope, transferManager)
    private val leftTransferManager = MyInternalTransferManager()
    private val rightTransferManager = MyInternalTransferManager()
    private val leftTabbed = TransportTabbed(coroutineScope, leftTransferManager)
    private val rightTabbed = TransportTabbed(coroutineScope, rightTransferManager)
    private val rootSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        leftTabbed.addLocalTab()
        rightTabbed.addSelectionTab()

        leftTransferManager.source = leftTabbed
        leftTransferManager.target = rightTabbed

        rightTransferManager.source = rightTabbed
        rightTransferManager.target = leftTabbed


        val scrollPane = JScrollPane(transferTable)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        leftTabbed.border = BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor)
        rightTabbed.border = BorderFactory.createMatteBorder(0, 1, 0, 0, DynamicColor.BorderColor)

        splitPane.resizeWeight = 0.5
        splitPane.leftComponent = leftTabbed
        splitPane.rightComponent = rightTabbed
        splitPane.border = BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor)

        rootSplitPane.resizeWeight = 0.7
        rootSplitPane.topComponent = splitPane
        rootSplitPane.bottomComponent = scrollPane

        add(rootSplitPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        splitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                splitPane.setDividerLocation(splitPane.resizeWeight)
            }
        })

        rootSplitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                rootSplitPane.setDividerLocation(rootSplitPane.resizeWeight)
            }
        })

        Disposer.register(this, leftTabbed)
        Disposer.register(this, rightTabbed)
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

    private inner class MyInternalTransferManager() : InternalTransferManager {
        lateinit var source: TransportTabbed
        lateinit var target: TransportTabbed

        override fun canTransfer(paths: List<Path>): Boolean {
            return target.getSelectedTransportPanel()?.workdir != null
        }

        override fun addTransfer(paths: List<Pair<Path, Boolean>>, mode: TransferMode): CompletableFuture<Unit> {
            if (paths.isEmpty()) return CompletableFuture.completedFuture(Unit)
            val workdir = (if (mode == TransferMode.Delete) source.getSelectedTransportPanel()?.workdir
            else target.getSelectedTransportPanel()?.workdir) ?: throw IllegalStateException()
            val future = CompletableFuture<Unit>()
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    for (pair in paths) {
                        val flag = doAddTransfer(workdir, pair.first, pair.second, mode, future)
                        if (flag != FileVisitResult.CONTINUE) break
                    }
                    future.complete(Unit)
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                    future.completeExceptionally(e)
                }
            }
            return future
        }


        private fun doAddTransfer(
            workdir: Path,
            path: Path,
            isDirectory: Boolean,
            mode: TransferMode,
            future: CompletableFuture<Unit>
        ): FileVisitResult {

            if (isDirectory.not()) {
                val transfer = createTransfer(path, workdir.resolve(path.name), false, StringUtils.EMPTY, mode)
                return if (transferManager.addTransfer(transfer)) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
            }

            val continued = AtomicBoolean(true)
            val queue = ArrayDeque<Transfer>()
            val isCancelled = { (future.isCancelled || future.isCompletedExceptionally).apply { continued.set(this) } }
            val basedir = if (isDirectory) workdir.resolve(path.name) else workdir

            Files.walkFileTree(path, object : FileVisitor<Path> {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    val parentId = queue.lastOrNull()?.id() ?: StringUtils.EMPTY
                    val transfer = if (mode == TransferMode.Delete)
                        createTransfer(dir, dir, true, parentId, mode)
                    else
                        createTransfer(dir, basedir.resolve(path.relativize(dir).pathString), true, parentId, mode)

                    queue.addLast(transfer)

                    if (transferManager.addTransfer(transfer).not()) {
                        continued.set(false)
                        return FileVisitResult.TERMINATE
                    }

                    return if (isCancelled.invoke()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (queue.isEmpty()) return FileVisitResult.SKIP_SIBLINGS

                    val transfer = if (mode == TransferMode.Delete)
                        createTransfer(file, file, false, queue.last().id(), mode)
                    else
                        createTransfer(
                            file, basedir.resolve(path.relativize(file).pathString),
                            false,
                            queue.last().id(), mode
                        )

                    if (transferManager.addTransfer(transfer).not()) {
                        continued.set(false)
                        return FileVisitResult.TERMINATE
                    }

                    return if (isCancelled.invoke()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path?,
                    exc: IOException
                ): FileVisitResult {
                    if (log.isErrorEnabled) {
                        log.error(exc.message, exc)
                        future.completeExceptionally(exc)
                    }
                    return FileVisitResult.TERMINATE
                }

                override fun postVisitDirectory(
                    dir: Path?,
                    exc: IOException?
                ): FileVisitResult {
                    val c = queue.removeLast()
                    if (c is TransferScanner) c.scanned()
                    return if (isCancelled.invoke()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                }

            })

            // 已经添加的则继续传输
            while (queue.isNotEmpty()) {
                val c = queue.removeLast()
                if (c is TransferScanner) c.scanned()
            }

            return if (continued.get()) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
        }

        private fun createTransfer(
            source: Path,
            target: Path,
            isDirectory: Boolean,
            parentId: String,
            mode: TransferMode
        ): Transfer {
            if (mode == TransferMode.Delete) {
                return DeleteTransfer(parentId, source, isDirectory, if (isDirectory) 1 else Files.size(source))
            }

            if (isDirectory) {
                return DirectoryTransfer(
                    parentId = parentId,
                    source = source,
                    target = target,
                )
            }

            return FileTransfer(
                parentId = parentId,
                source = source,
                target = target,
                size = Files.size(source)
            )
        }

    }
}