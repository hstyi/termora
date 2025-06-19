package app.termora.transport

import app.termora.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import org.jdesktop.swingx.treetable.DefaultTreeTableModel
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import kotlin.concurrent.withLock
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds


class TransferTableModel(private val coroutineScope: CoroutineScope) :
    DefaultTreeTableModel(DefaultMutableTreeTableNode()), Disposable, TransferManager {

    companion object {
        private val log = LoggerFactory.getLogger(TransferTableModel::class.java)

        const val COLUMN_COUNT = 8

        const val COLUMN_NAME = 0
        const val COLUMN_STATUS = 1
        const val COLUMN_PROGRESS = 2
        const val COLUMN_SIZE = 3
        const val COLUMN_SOURCE_PATH = 4
        const val COLUMN_TARGET_PATH = 5
        const val COLUMN_SPEED = 6
        const val COLUMN_ESTIMATED_TIME = 7
    }

    private val maxParallels = max(min(Runtime.getRuntime().availableProcessors(), 4), 1)
    private val map = ConcurrentHashMap<String, MyDefaultMutableTreeTableNode>()
    private val reporter = SizeReporter(coroutineScope)
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    init {
        setColumnIdentifiers(
            listOf(
                I18n.getString("termora.transport.jobs.table.name"),
                I18n.getString("termora.transport.jobs.table.status"),
                I18n.getString("termora.transport.jobs.table.progress"),
                I18n.getString("termora.transport.jobs.table.size"),
                I18n.getString("termora.transport.jobs.table.source-path"),
                I18n.getString("termora.transport.jobs.table.target-path"),
                I18n.getString("termora.transport.jobs.table.speed"),
                I18n.getString("termora.transport.jobs.table.estimated-time")
            )
        )

        consume()
    }


    override fun getRoot(): DefaultMutableTreeTableNode {
        return super.getRoot() as DefaultMutableTreeTableNode
    }

    override fun isCellEditable(node: Any?, column: Int): Boolean {
        return false
    }

    override fun getColumnCount(): Int {
        return COLUMN_COUNT
    }

    override fun addTransfer(transfer: Transfer) {
        SwingUtilities.invokeAndWait {
            val node = MyDefaultMutableTreeTableNode(transfer)
            val parent = map[transfer.parentId()] ?: getRoot()

            // 文件夹比较特殊，因为可能存在扫描中
            if (transfer.isDirectory().not()) {
                computeFilesize(node, transfer.size(), 0, setOf(ComputeField.Filesize))
            }

            map[transfer.id()] = node
            insertNodeInto(node, parent, parent.childCount)

            lock.withLock { condition.signalAll() }

        }


    }


    override fun removeTransfer(id: String) {
        SwingUtilities.invokeAndWait {
            val node = map.remove(id)
            if (node != null) removeNodeFromParent(node)
        }
    }

    private fun computeFilesize(
        node: MyDefaultMutableTreeTableNode,
        size: Long,
        time: Long,
        fields: Set<ComputeField>
    ) {
        if (fields.contains(ComputeField.Transferred)) {
            node.totalBytesTransferred.addAndGet(size)
        }
        if (fields.contains(ComputeField.Counter)) {
            node.counter.addBytes(size, time)
        }

        var p = map[node.transfer.parentId()]
        while (p != null) {
            for (field in fields) {
                when (field) {
                    ComputeField.Filesize -> p.filesize.addAndGet(size)
                    ComputeField.Transferred -> p.totalBytesTransferred.addAndGet(size)
                    ComputeField.Counter -> p.counter.addBytes(size, time)
                }
            }
            p = map[p.transfer.parentId()]
        }
    }

    private fun canTransfer(node: MyDefaultMutableTreeTableNode): Boolean {
        var p: MyDefaultMutableTreeTableNode? = node
        while (p != null) {
            if (map.containsKey(p.transfer.id()).not()) {
                return false
            }
            p = map[p.transfer.parentId()]
        }

        return true
    }

    private fun consume() {
        // 开启传输
        repeat(maxParallels) { coroutineScope.launch { transfer() } }
    }


    private fun getReadyTransfer(): MyDefaultMutableTreeTableNode? {
        assertEventDispatchThread()

        val stack = ArrayDeque<MyDefaultMutableTreeTableNode>()
        val root = getRoot()
        for (i in root.childCount - 1 downTo 0) {
            val child = root.getChildAt(i)
            if (child is MyDefaultMutableTreeTableNode) {
                stack.addLast(child)
            }
        }

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val transfer = node.transfer
            val parent = node.parent as? MyDefaultMutableTreeTableNode

            // 如果父文件夹正在创建，那么等待创建完毕
            // 顺序一定是先创建文件夹后传输文件
            if (parent != null) {
                if (parent.state() == State.Processing && parent.directoryCreated.not()) {
                    continue
                }
            }

            // 如果是文件夹并且已经创建，那么尝试去删除
            if (transfer.isDirectory() && node.directoryCreated) {
                removeCompleted(node)
            }

            if (node.state() == State.Ready) {
                node.changeState(State.Processing)
                return node
            }

            for (i in node.childCount - 1 downTo 0) {
                val child = node.getChildAt(i)
                if (child is MyDefaultMutableTreeTableNode) {
                    stack.addLast(child)
                }
            }
        }

        return null
    }


    private suspend fun transfer() {
        while (coroutineScope.isActive) {
            try {
                val node = withContext(Dispatchers.Swing) { getReadyTransfer() }
                if (node == null) {
                    if (map.isEmpty()) {
                        lock.withLock { condition.await() }
                    } else {
                        lock.withLock { condition.await(250, TimeUnit.MILLISECONDS) }
                    }
                    continue
                } else if (canTransfer(node)) {
                    doTransfer(node)
                }
                lock.withLock { condition.signalAll() }
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }

    private suspend fun doTransfer(node: MyDefaultMutableTreeTableNode) {

        val transfer = node.transfer
        val isDirectory = transfer.isDirectory()
        val isFile = isDirectory.not()
        var transferred = false

        try {

            var len = 0
            while (transfer.transfer().also { len = it } > 0) {
                transferred = true
                if (map.containsKey(transfer.id()).not()) {
                    // 减去总大小
                    computeFilesize(node, -node.filesize.get(), 0, setOf(ComputeField.Filesize))
                    // 减去传输的大小
                    computeFilesize(node, -node.totalBytesTransferred.get(), 0, setOf(ComputeField.Transferred))
                    break
                }
                val c = len.toLong()
                reporter.report(node, c, System.currentTimeMillis())
            }

            withContext(Dispatchers.Swing) {
                if (isDirectory) {
                    node.directoryCreated = true
                } else if (isFile) {
                    node.changeState(State.Done)
                    removeCompleted(node)
                }
            }

        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            node.changeState(State.Failed)
            throw e
        } finally {
            if (transferred && isFile && transfer is Closeable)
                IOUtils.closeQuietly(transfer)
        }
    }

    private fun removeCompleted(node: MyDefaultMutableTreeTableNode) {
        assertEventDispatchThread()

        if (node == getRoot()) return
        if (node.transfer.isDirectory() && node.childCount > 0) return
        if (node.transfer.scanning()) return
        if (node.parent == null) return

        removeNodeFromParent(node)
        map.remove(node.transfer.id())

    }

    private class MyDefaultMutableTreeTableNode(transfer: Transfer) : DefaultMutableTreeTableNode(transfer) {

        /**
         * 文件总大小
         */
        val filesize = AtomicLong(0)

        /**
         * 文件传输的大小
         */
        val totalBytesTransferred = AtomicLong(0)

        /**
         * 速率计数
         */
        val counter = SlidingWindowByteCounter()

        /**
         * 状态
         */
        var state = State.Ready

        var directoryCreated = false

        override fun getColumnCount(): Int {
            return COLUMN_COUNT
        }

        val transfer get() = getUserObject() as Transfer

        override fun getValueAt(column: Int): Any? {
            val filesize = if (transfer.isDirectory()) filesize.get() else transfer.size()
            val totalBytesTransferred = totalBytesTransferred.get()
            val isProcessing = state() == State.Processing
            val speed = counter.getLastSecondBytes()
            val estimatedTime = if (isProcessing && speed > 0) (filesize - totalBytesTransferred) / speed else 0
            val state = state()

            return when (column) {
                COLUMN_NAME -> transfer.source().name
                COLUMN_STATUS -> formatStatus(state)
                COLUMN_SOURCE_PATH -> transfer.source().absolutePathString()
                COLUMN_TARGET_PATH -> transfer.target().absolutePathString()
                COLUMN_SIZE -> "${formatBytes(totalBytesTransferred)} / ${formatBytes(filesize)}"
                COLUMN_SPEED -> if (isProcessing) "${formatBytes(speed)}/s" else "-"
                COLUMN_ESTIMATED_TIME -> if (isProcessing) formatSeconds(estimatedTime) else "-"
                else -> StringUtils.EMPTY
            }
        }

        private fun formatStatus(state: State): String {
            return when (state) {
                State.Processing -> I18n.getString("termora.transport.sftp.status.transporting")
                State.Ready -> I18n.getString("termora.transport.sftp.status.waiting")
                State.Done -> I18n.getString("termora.transport.sftp.status.done")
                State.Failed -> I18n.getString("termora.transport.sftp.status.failed")
            }
        }

        fun state(): State {
            return state
        }

        fun changeState(state: State) {
            if (this.state == State.Done || this.state == State.Failed) {
                throw IllegalStateException()
            }

            if (this.state == State.Processing && state == State.Ready) {
                throw IllegalStateException()
            }

            this.state = state
        }

    }

    private enum class ComputeField {
        Filesize,
        Transferred,
        Counter
    }


    private enum class State {
        Ready,
        Processing,
        Failed,
        Done,
    }

    private inner class SizeReporter(private val coroutineScope: CoroutineScope) {

        private val events = ConcurrentLinkedQueue<Triple<MyDefaultMutableTreeTableNode, Long, Long>>()

        init {
            collect()
        }

        fun report(node: MyDefaultMutableTreeTableNode, bytes: Long, time: Long) {
            events.add(Triple(node, bytes, time))
        }

        private fun collect() {
            // 异步上报数据
            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    val time = System.currentTimeMillis()
                    val map = linkedMapOf<MyDefaultMutableTreeTableNode, Long>()

                    // 收集
                    while (events.isNotEmpty() && events.peek().second < time) {
                        val (a, b) = events.poll()
                        map[a] = map.computeIfAbsent(a) { 0 } + b
                    }

                    if (map.isNotEmpty()) {
                        for ((a, b) in map) {
                            if (b > 0) {
                                computeFilesize(a, b, time, setOf(ComputeField.Counter, ComputeField.Transferred))
                            }
                        }
                    }


                    delay(500.milliseconds)
                }
            }
        }


    }

    class SlidingWindowByteCounter {
        private val events = ConcurrentLinkedQueue<Pair<Long, Long>>()
        private val oneSecondInMillis = TimeUnit.SECONDS.toMillis(1)

        fun addBytes(bytes: Long, time: Long) {

            // 添加当前事件
            events.add(time to bytes)

            // 移除过期事件（超过 1 秒的记录）
            while (events.isNotEmpty() && events.peek().first < time - oneSecondInMillis) {
                events.poll()
            }

        }

        fun getLastSecondBytes(): Long {
            val currentTime = System.currentTimeMillis()
            // 累加最近 1 秒内的字节数
            return events.filter { it.first >= currentTime - oneSecondInMillis }
                .sumOf { it.second }
        }

    }

}