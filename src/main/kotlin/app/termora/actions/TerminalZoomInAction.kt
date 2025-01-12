package app.termora.actions

import app.termora.Database

class TerminalZoomInAction : TerminalZoomAction() {
    companion object {
        const val ZOOM_IN = "TerminalZoomInAction"
    }

    init {
        putValue(ACTION_COMMAND_KEY, ZOOM_IN)
        putValue(SHORT_DESCRIPTION, "Terminal Zoom In")
    }

    override fun zoom(): Boolean {
        Database.getDatabase().terminal.fontSize += 2
        return true
    }
}