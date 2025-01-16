package app.termora.transport

import app.termora.terminal.DataKey

object TransportDataProviders {
    val LeftFileSystemPanel = DataKey(FileSystemPanel::class)
    val RightFileSystemPanel = DataKey(FileSystemPanel::class)

    val LeftFileSystemTabbed = DataKey(FileSystemTabbed::class)
    val RightFileSystemTabbed = DataKey(FileSystemTabbed::class)

    val TransportManager = DataKey(app.termora.transport.TransportManager::class)

    val TransportPanel = DataKey(app.termora.transport.TransportPanel::class)
}


