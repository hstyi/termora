package app.termora.transport

interface TransferScanner {
    fun scanning(): Boolean
    fun scanned()
}