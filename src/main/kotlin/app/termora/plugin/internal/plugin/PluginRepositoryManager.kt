package app.termora.plugin.internal.plugin

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.EnableManager
import org.apache.commons.lang3.StringUtils

internal class PluginRepositoryManager private constructor() {
    companion object {
        private const val KEY = "PluginRepositories"
        fun getInstance(): PluginRepositoryManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(PluginRepositoryManager::class) { PluginRepositoryManager() }
        }
    }

    private val enableManager get() = EnableManager.getInstance()

    fun addRepository(url: String) {
        synchronized(this) {
            val repositories = getRepositories().toMutableList()
            repositories.add(url)
            enableManager.setFlag(KEY, ohMyJson.encodeToString(repositories))
        }
    }

    fun removeRepository(url: String) {
        synchronized(this) {
            val repositories = getRepositories().toMutableList()
            repositories.removeIf { it == url }
            enableManager.setFlag(KEY, ohMyJson.encodeToString(repositories))
        }
    }

    fun getRepositories(): List<String> {
        synchronized(this) {
            val text = enableManager.getFlag(KEY, StringUtils.EMPTY)
            if (text.isBlank()) return emptyList()
            return runCatching { ohMyJson.decodeFromString<List<String>>(text) }
                .getOrNull() ?: emptyList()
        }
    }
}