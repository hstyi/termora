package app.termora.transfer

interface TransferHandler {
    companion object {
        val EMPTY: TransferHandler = object : TransferHandler {
            override fun isDisposed() = false
        }
    }

    /**
     * 持有者已经销毁
     */
    fun isDisposed(): Boolean
}