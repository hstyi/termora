package app.termora.highlight

import app.termora.ApplicationScope
import app.termora.TerminalPanelFactory
import app.termora.Database
import org.slf4j.LoggerFactory

class KeywordHighlightManager private constructor() {

    companion object {
        fun getInstance(): KeywordHighlightManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeywordHighlightManager::class) { KeywordHighlightManager() }
        }

        private val log = LoggerFactory.getLogger(KeywordHighlightManager::class.java)
    }

    private val database by lazy { Database.getDatabase() }
    private val keywordHighlights = mutableMapOf<String, KeywordHighlight>()

    init {
        keywordHighlights.putAll(database.getKeywordHighlights().associateBy { it.id })
    }


    fun addKeywordHighlight(keywordHighlight: KeywordHighlight) {
        database.addKeywordHighlight(keywordHighlight)
        keywordHighlights[keywordHighlight.id] = keywordHighlight
        ApplicationScope.windowScopes().forEach { TerminalPanelFactory.getInstance(it).repaintAll() }

        if (log.isDebugEnabled) {
            log.debug("Keyword highlighter added. {}", keywordHighlight)
        }
    }

    fun removeKeywordHighlight(id: String) {
        database.removeKeywordHighlight(id)
        keywordHighlights.remove(id)
        ApplicationScope.windowScopes().forEach { TerminalPanelFactory.getInstance(it).repaintAll() }

        if (log.isDebugEnabled) {
            log.debug("Keyword highlighter removed. {}", id)
        }
    }

    fun getKeywordHighlights(): List<KeywordHighlight> {
        return keywordHighlights.values.sortedBy { it.sort }
    }

    fun getKeywordHighlight(id: String): KeywordHighlight? {
        return keywordHighlights[id]
    }
}