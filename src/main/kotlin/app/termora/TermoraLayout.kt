package app.termora

import app.termora.database.DatabaseManager

enum class TermoraLayout {
    /**
     * Split
     */
    Fence,

    Screen, ;

    companion object {
        val Layout by lazy {
            runCatching { TermoraLayout.valueOf(DatabaseManager.getInstance().appearance.layout) }.getOrNull() ?: Screen
        }
    }
}