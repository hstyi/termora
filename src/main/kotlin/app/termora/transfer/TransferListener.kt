package app.termora.transfer

import java.util.*

interface TransferListener : EventListener {
    /**
     * 状态变化
     */
    fun onTransferChanged(transfer: Transfer, state: TransferTreeTableNode.State)
}