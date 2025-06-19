package app.termora.transport

import java.nio.file.FileSystem


class TransportSupport(
    val fileSystem: FileSystem,
    val path: String
)