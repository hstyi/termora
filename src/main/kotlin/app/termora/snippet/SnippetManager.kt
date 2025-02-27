package app.termora.snippet

import app.termora.ApplicationScope
import app.termora.Database
import app.termora.assertEventDispatchThread


class SnippetManager private constructor() {
    companion object {
        fun getInstance(): SnippetManager {
            return ApplicationScope.forApplicationScope().getOrCreate(SnippetManager::class) { SnippetManager() }
        }
    }

    private val database get() = Database.getDatabase()
    private var snippets = mutableMapOf<String, Snippet>()

    /**
     * 修改缓存并存入数据库
     */
    fun addSnippet(snippet: Snippet) {
        assertEventDispatchThread()
        database.addSnippet(snippet)
        if (snippet.deleted) {
            snippets.entries.removeIf { it.value.id == snippet.id || it.value.parentId == snippet.id }
        } else {
            snippets[snippet.id] = snippet
        }
    }

    /**
     * 第一次调用从数据库中获取，后续从缓存中获取
     */
    fun snippets(): List<Snippet> {
        if (snippets.isEmpty()) {
            database.getSnippets().filter { !it.deleted }
                .forEach { snippets[it.id] = it }
        }
        return snippets.values.filter { !it.deleted }
            .sortedWith(compareBy<Snippet> { if (it.type == SnippetType.Folder) 0 else 1 }.thenBy { it.sort })
    }


}