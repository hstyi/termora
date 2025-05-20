package app.termora.snippet

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeleteDataManager
import app.termora.account.AccountManager
import app.termora.assertEventDispatchThread
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.OwnerType


class SnippetManager private constructor() {
    companion object {
        fun getInstance(): SnippetManager {
            return ApplicationScope.forApplicationScope().getOrCreate(SnippetManager::class) { SnippetManager() }
        }
    }

    private val database get() = DatabaseManager.getInstance()
    private var snippets = mutableMapOf<String, Snippet>()

    /**
     * 修改缓存并存入数据库
     */
    fun addSnippet(snippet: Snippet) {
        assertEventDispatchThread()
        if (snippet.deleted) {
            removeSnippet(snippet.id)
        } else {
            val accountId = AccountManager.getInstance().getAccountId()
            database.save(
                accountId, OwnerType.User, snippet.id,
                DataType.Snippet, ohMyJson.encodeToString(snippet)
            )
            snippets[snippet.id] = snippet
        }
    }

    fun removeSnippet(id: String) {
        snippets.entries.removeIf { it.value.id == id || it.value.parentId == id }
        database.delete(id)
        DeleteDataManager.getInstance().removeSnippet(id)
    }

    /**
     * 第一次调用从数据库中获取，后续从缓存中获取
     */
    fun snippets(): List<Snippet> {
        if (snippets.isEmpty()) {
            database.data<Snippet>(DataType.Snippet)
                .forEach { snippets[it.id] = it }
        }
        return snippets.values.filter { !it.deleted }
            .sortedWith(compareBy<Snippet> { if (it.type == SnippetType.Folder) 0 else 1 }.thenBy { it.sort })
    }


}