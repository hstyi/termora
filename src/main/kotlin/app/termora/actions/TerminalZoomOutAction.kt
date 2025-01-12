package app.termora.actions

import app.termora.Database
import kotlin.math.max

class TerminalZoomOutAction : TerminalZoomAction() {
    companion object {
        const val ZOOM_OUT = "TerminalZoomOutAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, ZOOM_OUT)
        putValue(SHORT_DESCRIPTION, "Terminal Zoom Out")
    }

    override fun zoom(): Boolean {
        val oldFontSize = fontSize
        Database.getDatabase().terminal.fontSize = max(fontSize - 2, 9)
        return oldFontSize != fontSize
    }
}