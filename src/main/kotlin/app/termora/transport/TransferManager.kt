package app.termora.transport

interface TransferManager {


    /**
     * 添加传输任务
     */
    fun addTransfer(transfer: Transfer)

    /**
     * 移除传输任务
     */
    fun removeTransfer(id: String)

}