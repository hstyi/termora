package app.termora.transfer.internal.sftp

import app.termora.FrameExtension
import app.termora.TermoraFrame
import app.termora.actions.DataProviders
import app.termora.database.DatabaseManager
import app.termora.transfer.TransportTerminalTab

class SFTPFrameExtension private constructor() : FrameExtension {
    companion object {
        val instance = SFTPFrameExtension()
    }

    private val sftp get() = DatabaseManager.getInstance().sftp

    override fun customize(frame: TermoraFrame) {
        val terminalTabbed = frame.getData(DataProviders.TerminalTabbed) ?: return
        if (sftp.pinTab) {
            terminalTabbed.addTerminalTab(TransportTerminalTab(), false)
        }
    }

}