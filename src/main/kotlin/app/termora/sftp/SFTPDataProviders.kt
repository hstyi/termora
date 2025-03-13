package app.termora.sftp

import app.termora.terminal.DataKey

object SFTPDataProviders {
    val TransportManager = DataKey(app.termora.sftp.TransportManager::class)
    val FileSystemViewPanel = DataKey(app.termora.sftp.FileSystemViewPanel::class)
    val CoroutineScope = DataKey(kotlinx.coroutines.CoroutineScope::class)
    val FileSystemViewTable = DataKey(app.termora.sftp.FileSystemViewTable::class)
    val LeftSFTPTabbed = DataKey(SFTPTabbed::class)
    val RightSFTPTabbed = DataKey(SFTPTabbed::class)
}