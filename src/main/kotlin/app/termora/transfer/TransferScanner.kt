package app.termora.transfer

interface TransferScanner {
    fun scanning(): Boolean
    fun scanned()
}