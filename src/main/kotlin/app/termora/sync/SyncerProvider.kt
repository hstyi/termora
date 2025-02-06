package app.termora.sync

import app.termora.ApplicationScope

class SyncerProvider private constructor() {
    companion object {
        fun getInstance(): SyncerProvider {
            return ApplicationScope.forApplicationScope().getOrCreate(SyncerProvider::class) { SyncerProvider() }
        }
    }


    fun getSyncer(type: SyncType): Syncer {
        return when (type) {
            SyncType.GitHub -> GitHubSyncer.getInstance()
            SyncType.Gitee -> GiteeSyncer.getInstance()
            SyncType.GitLab -> GitLabSyncer.getInstance()
            SyncType.WebDAV -> WebDAVSyncer.getInstance()
        }
    }
}