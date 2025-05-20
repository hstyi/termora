package app.termora.sync

interface Syncer {
    fun pull(config: SyncConfig): GistResponse

    fun push(config: SyncConfig): GistResponse
}