package app.termora.sftp

import org.apache.commons.vfs2.FileSystem


interface FileSystemProvider {
    fun getFileSystem(): FileSystem
    fun setFileSystem(fileSystem: FileSystem)
}