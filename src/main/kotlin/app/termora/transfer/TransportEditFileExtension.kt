package app.termora.transfer

import app.termora.Disposable
import app.termora.plugin.Extension
import java.awt.Window
import java.nio.file.Path

interface TransportEditFileExtension : Extension {
    fun edit(owner: Window, path: Path): Disposable
}