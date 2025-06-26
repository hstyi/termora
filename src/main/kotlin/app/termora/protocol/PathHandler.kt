package app.termora.protocol

import app.termora.Disposable
import org.apache.commons.io.IOUtils
import java.nio.file.FileSystem
import java.nio.file.Path

open class PathHandler(val fileSystem: FileSystem, val path: Path) : Disposable {
    override fun dispose() {
        IOUtils.closeQuietly(fileSystem)
    }
}