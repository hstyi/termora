package app.termora.transport

import app.termora.I18n
import app.termora.formatBytes
import app.termora.formatSeconds
import app.termora.transport.TransferTableModel.Companion.COLUMN_COUNT
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.math.max

class TransferTreeTableNode(transfer: Transfer) : DefaultMutableTreeTableNode(transfer) {

    enum class State {
        Ready,
        Processing,
        Failed,
        Done,
    }

    /**
     * 文件总大小，删除时文件夹也算数量
     */
    val filesize = AtomicLong(transfer.size())

    /**
     * 文件传输的大小
     */
    val transferred = AtomicLong(0)

    /**
     * 速率计数
     */
    val counter = TransferTableModel.SlidingWindowByteCounter()

    /**
     * 状态
     */
    private var state = State.Ready

    var directoryCreated = false

    override fun getColumnCount(): Int {
        return COLUMN_COUNT
    }

    val transfer get() = getUserObject() as Transfer

    override fun getValueAt(column: Int): Any? {
        val filesize = if (transfer.isDirectory()) filesize.get() else transfer.size()
        val totalBytesTransferred = transferred.get()
        val isProcessing = state() == State.Processing
        val speed = counter.getLastSecondBytes()
        val estimatedTime = max(if (isProcessing && speed > 0) (filesize - totalBytesTransferred) / speed else 0, 0)
        val state = state()

        return when (column) {
            TransferTableModel.COLUMN_NAME -> transfer.source().name
            TransferTableModel.COLUMN_STATUS -> formatStatus(state)
            TransferTableModel.COLUMN_SOURCE_PATH -> transfer.source().absolutePathString()
            TransferTableModel.COLUMN_TARGET_PATH -> transfer.target().absolutePathString()
            TransferTableModel.COLUMN_SIZE -> "${formatBytes(totalBytesTransferred)} / ${formatBytes(filesize)}"
            TransferTableModel.COLUMN_SPEED -> if (isProcessing) "${formatBytes(speed)}/s" else "-"
            TransferTableModel.COLUMN_ESTIMATED_TIME -> if (isProcessing) formatSeconds(estimatedTime) else "-"
            TransferTableModel.COLUMN_PROGRESS -> this
            else -> StringUtils.EMPTY
        }
    }

    private fun formatStatus(state: State): String {
        if (transfer is DeleteTransfer && state == State.Processing) {
            return I18n.getString("termora.transport.sftp.status.deleting")
        }

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


    fun tryChangeState(state: State): Boolean {
        if (this.state == State.Done || this.state == State.Failed) {
            return false
        }

        if (this.state == State.Processing && state == State.Ready) {
            return false
        }

        this.state = state

        return true
    }

}