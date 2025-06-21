package app.termora.plugins.editor

import app.termora.Disposable
import app.termora.Disposer
import app.termora.transfer.TransportEditFileExtension
import java.awt.Window
import java.nio.file.Path
import javax.swing.SwingUtilities

class MyTransportEditFileExtension private constructor() : TransportEditFileExtension {
    companion object {
        val instance = MyTransportEditFileExtension()
    }

    override fun edit(owner: Window, path: Path): Disposable {
        val disposable = Disposer.newDisposable()
        SwingUtilities.invokeLater { EditorDialog(path, owner, disposable).isVisible = true }
        return disposable
    }
}