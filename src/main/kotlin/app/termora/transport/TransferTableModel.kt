package app.termora.transport

import app.termora.Disposable
import app.termora.I18n
import app.termora.assertEventDispatchThread
import app.termora.transport.TransferTreeTableNode.State
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okio.withLock
import org.apache.commons.io.IOUtils
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import org.jdesktop.swingx.treetable.DefaultTreeTableModel
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import kotlin.math.abs
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
    private val map = ConcurrentHashMap<String, TransferTreeTableNode>()
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

    override fun addTransfer(transfer: Transfer): Boolean {
        val node = TransferTreeTableNode(transfer)
        val parent = if (transfer.parentId().isBlank()) getRoot() else map[transfer.parentId()] ?: return false
        val result = AtomicBoolean(false)

        SwingUtilities.invokeAndWait {
            if (validGrandfather(transfer.parentId())) {
                map[transfer.id()] = node
                insertNodeInto(node, parent, parent.childCount)
                result.set(true)
            }
        }

        if (result.get()) {
            // 文件立即计算大小
            if (transfer.isDirectory().not() || transfer is DeleteTransfer) {
                computeFilesize(node, transfer.size(), 0, setOf(ComputeField.Filesize))
            }
            lock.withLock { condition.signalAll() }
        }


        return result.get()
    }

    /**
     * 获取祖先的状态，如果祖先状态不正常，那么子直接定义为失败
     *
     * @return true 正常
     */
    private fun validGrandfather(parentId: String): Boolean {
        if (parentId.isBlank()) return true

        var parent = map[parentId]
        if (parent == null) return false

        while (parent != null) {
            if (map.containsKey(parent.transfer.id()).not()) return false
            if (parent.state() == State.Failed) return false
            if (parent == getRoot()) return true
            if (parent.transfer.parentId().isBlank()) return true
            parent = parent.parent as? TransferTreeTableNode
        }

        return false
    }

    override fun removeTransfer(id: String) {
        assertEventDispatchThread()

        val rootNode = map[id] ?: return
        val stack = ArrayDeque<Pair<TransferTreeTableNode, Boolean>>()
        stack.addLast(rootNode to false)

        while (stack.isNotEmpty()) {
            val (node, visitedChildren) = stack.removeLast()
            if (visitedChildren || node.childCount == 0) {
                val failed = node.state() != State.Done
                val transfer = node.transfer

                // 定义为失败
                node.tryChangeState(State.Failed)
                // 移除
                map.remove(node.transfer.id())
                removeNodeFromParent(node)

                // 如果删除时还在传输，那么需要减去大小
                // 如果是传输任务，文件夹是不处理的，因为文件夹的大小来自文件
                // 如果是删除任务，需要减去大小，删除任务的文件大小最小的：1
                if ((failed && transfer.isDirectory().not()) || (failed && transfer is DeleteTransfer)) {
                    // 收集一次，确保数据实时
                    reporter.collect()
                    // 该文件已传输的大小
                    val transferred = node.transferred.get()
                    // 减去总大小，总大小就是减去尚未传输的数量
                    computeFilesize(node, -abs(node.transfer.size() - transferred), 0, setOf(ComputeField.Filesize))
                }

                continue
            }

            stack.addLast(node to true)
            for (i in node.childCount - 1 downTo 0) {
                val child = node.getChildAt(i)
                if (child is TransferTreeTableNode) {
                    stack.addLast(child to false)
                }
            }
        }
    }

    private fun computeFilesize(
        node: TransferTreeTableNode,
        size: Long,
        time: Long,
        fields: Set<ComputeField>
    ) {
        if (fields.contains(ComputeField.Counter)) {
            node.counter.addBytes(size, time)
        }

        if (fields.contains(ComputeField.Transferred)) {
            node.transferred.addAndGet(size)
        }

        var p = map[node.transfer.parentId()]
        while (p != null) {
            for (field in fields) {
                when (field) {
                    ComputeField.Filesize -> p.filesize.addAndGet(size)
                    ComputeField.Transferred -> p.transferred.addAndGet(size)
                    ComputeField.Counter -> p.counter.addBytes(size, time)
                }
            }
            p = map[p.transfer.parentId()]
        }
    }

    private fun canTransfer(node: TransferTreeTableNode): Boolean {
        var p: TransferTreeTableNode? = node
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


    private fun getReadyTransfer(): TransferTreeTableNode? {
        assertEventDispatchThread()

        val stack = ArrayDeque<TransferTreeTableNode>()
        val root = getRoot()

        for (i in root.childCount - 1 downTo 0) {
            val child = root.getChildAt(i)
            if (child is TransferTreeTableNode) {
                stack.addLast(child)
            }
        }

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val transfer = node.transfer
            val parent = node.parent as? TransferTreeTableNode

            // 删除文件和传输文件完全相反，传输文件是先创建文件夹后传输文件
            // 删除文件，是先删除文件后删除文件夹
            if (transfer is DeleteTransfer) {
                if (node.state() != State.Failed) {
                    val c = getReadyDeleteTransfer(node)
                    if (c != null) {
                        return c
                    }
                }
                continue
            }

            // 如果父文件夹正在创建，那么等待创建完毕
            // 顺序一定是先创建文件夹后传输文件
            if (parent != null) {
                if (parent.state() == State.Processing && parent.directoryCreated.not()) {
                    continue
                }
                // 父亲失败则子失败
                if (parent.state() == State.Failed && node.state() != State.Failed) {
                    node.changeState(State.Failed)
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
                if (child is TransferTreeTableNode) {
                    stack.addLast(child)
                }
            }
        }

        return null
    }

    /**
     * 深度优先
     */
    private fun getReadyDeleteTransfer(
        treeNode: TransferTreeTableNode,
    ): TransferTreeTableNode? {
        val stack = ArrayDeque<TransferTreeTableNode>()
        stack.addLast(treeNode)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val transfer = node.transfer
            if (transfer.isDirectory().not()) {
                if (node.state() == State.Ready) {
                    node.changeState(State.Processing)
                    return node
                }
            }

            // 如果是文件夹并且已经扫描完毕
            if (transfer.isDirectory() && transfer.scanning().not() && node.childCount < 1) {
                if (node.state() == State.Ready) {
                    node.changeState(State.Processing)
                    return node
                }
            }

            for (i in node.childCount - 1 downTo 0) {
                val child = node.getChildAt(i)
                if (child is TransferTreeTableNode) {
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
                        lock.withLock { condition.await(1, TimeUnit.SECONDS) }
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

    private suspend fun doTransfer(node: TransferTreeTableNode) {

        val transfer = node.transfer
        val isDirectory = transfer.isDirectory()
        val isFile = isDirectory.not()

        try {

            var len = 0L

            while (transfer.transfer().also { len = it } > 0) {
                // 如果不存在则表示已经被删除了
                if (map.containsKey(transfer.id()).not()) throw UserCanceledException()
                if (node.state() != State.Processing) throw UserCanceledException()
                // 异步上报，因为数据量非常大，所以采用异步
                reporter.report(node, len, System.currentTimeMillis())
            }

            withContext(Dispatchers.Swing) {
                if (isDirectory) {
                    node.directoryCreated = true
                }

                // 删除是比较特殊的，如果是删除，无论文件夹/文件 在完成的那一刻就已经删除了
                if (isFile || (transfer is DeleteTransfer)) {
                    node.changeState(State.Done)
                }

                removeCompleted(node)
            }

        } catch (e: Exception) {
            // 立即失败
            node.tryChangeState(State.Failed)
            if (e !is UserCanceledException) throw e
        } finally {
            if (transfer is Closeable) IOUtils.closeQuietly(transfer)
        }
    }

    private fun removeCompleted(node: TransferTreeTableNode) {

        if (node == getRoot()) return
        if (node.transfer.isDirectory() && node.childCount > 0) return
        if (node.transfer.scanning()) return
        if (node.parent == null) return

        assertEventDispatchThread()

        removeTransfer(node.transfer.id())

    }

    private class UserCanceledException : RuntimeException()


    private enum class ComputeField {
        Filesize,
        Transferred,
        Counter
    }


    private inner class SizeReporter(private val coroutineScope: CoroutineScope) {

        private val events = ConcurrentLinkedQueue<Triple<TransferTreeTableNode, Long, Long>>()
        private val lock = ReentrantLock()

        init {
            scheduleCollect()
        }

        fun report(node: TransferTreeTableNode, bytes: Long, time: Long) {
            events.add(Triple(node, bytes, time))
        }

        private fun scheduleCollect() {
            // 异步上报数据
            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    collect()
                    delay(500.milliseconds)
                }
            }
        }

        fun collect() {
            lock.withLock {
                val time = System.currentTimeMillis()
                val map = linkedMapOf<TransferTreeTableNode, Long>()

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