package app.termora.highlight

import app.termora.Application.ohMyJson
import app.termora.ApplicationScope
import app.termora.DeleteDataManager
import app.termora.TerminalPanelFactory
import app.termora.account.AccountManager
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.OwnerType
import org.slf4j.LoggerFactory

class KeywordHighlightManager private constructor() {

    companion object {
        fun getInstance(): KeywordHighlightManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeywordHighlightManager::class) { KeywordHighlightManager() }
        }

        private val log = LoggerFactory.getLogger(KeywordHighlightManager::class.java)
    }

    private val database get() = DatabaseManager.getInstance()
    private val keywordHighlights = mutableMapOf<String, KeywordHighlight>()


    fun addKeywordHighlight(keywordHighlight: KeywordHighlight) {

        val accountId = AccountManager.getInstance().getAccountId()
        database.save(
            accountId, OwnerType.User, keywordHighlight.id,
            DataType.KeywordHighlight, ohMyJson.encodeToString(keywordHighlight)
        )

        keywordHighlights[keywordHighlight.id] = keywordHighlight
        TerminalPanelFactory.getInstance().repaintAll()

        if (log.isDebugEnabled) {
            log.debug("Keyword highlighter added. {}", keywordHighlight)
        }
    }

    fun removeKeywordHighlight(id: String) {
        keywordHighlights.remove(id)
        database.delete(id)
        TerminalPanelFactory.getInstance().repaintAll()
        DeleteDataManager.getInstance().removeKeywordHighlight(id)

        if (log.isDebugEnabled) {
            log.debug("Keyword highlighter removed. {}", id)
        }
    }

    fun getKeywordHighlights(): List<KeywordHighlight> {
        if (keywordHighlights.isEmpty()) {
            database.data<KeywordHighlight>(DataType.KeywordHighlight)
                .forEach { keywordHighlights[it.id] = it }
        }
        return keywordHighlights.values.sortedBy { it.sort }
    }

    fun getKeywordHighlight(id: String): KeywordHighlight? {
        return keywordHighlights[id]
    }
}