package app.termora.actions

import app.termora.Database

class TerminalZoomResetAction : TerminalZoomAction() {
    companion object {
        const val ZOOM_RESET = "TerminalZoomResetAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, ZOOM_RESET)
        putValue(SHORT_DESCRIPTION, "Terminal Zoom Reset")
    }

    private val defaultFontSize = 16

    override fun zoom(): Boolean {
        if (fontSize == defaultFontSize) {
            return false
        }
        Database.getDatabase().terminal.fontSize = 16
        return true
    }
}