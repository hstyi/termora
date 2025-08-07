package app.termora.plugins.editor

import app.termora.NamedI18n
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object EditorI18n : NamedI18n("i18n/messages") {
    private val log = LoggerFactory.getLogger(EditorI18n::class.java)

    override fun getLogger(): Logger {
        return log
    }
}