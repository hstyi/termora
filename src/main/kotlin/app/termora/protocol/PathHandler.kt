package app.termora.protocol

import app.termora.Disposable
import java.nio.file.FileSystem
import java.nio.file.Path

open class PathHandler(val fileSystem: FileSystem, val path: Path) : Disposable {

}