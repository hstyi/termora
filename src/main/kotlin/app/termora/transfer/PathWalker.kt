package app.termora.transfer

import app.termora.transfer.PathWalker.EmptyBasicFileAttributes.Companion.INSTANCE
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.fs.SftpFileSystem
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.io.path.absolutePathString

object PathWalker {

    fun walkFileTree(path: Path, visitor: FileVisitor<Path>) {
        if (path.fileSystem is SftpFileSystem) {
            val fileSystem = path.fileSystem as SftpFileSystem
            fileSystem.client.use { walkFileTree(path, it, visitor) }
        } else {
            Files.walkFileTree(path, visitor)
        }

    }

    private fun walkFileTree(path: Path, sftpClient: SftpClient, visitor: FileVisitor<Path>): Boolean {
        if (visitor.preVisitDirectory(path, INSTANCE) == FileVisitResult.TERMINATE) {
            return false
        }
        for (e in sftpClient.readDir(path.absolutePathString())) {
            if (e.filename == ".." || e.filename == ".") {
                continue
            }
            if (e.attributes.isDirectory) {
                if (walkFileTree(path.resolve(e.filename), sftpClient, visitor).not()) {
                    return false
                }
            } else {
                if (visitor.visitFile(path.resolve(e.filename), INSTANCE) == FileVisitResult.TERMINATE) {
                    return false
                }
            }
        }
        return visitor.postVisitDirectory(path, null) == FileVisitResult.CONTINUE
    }


    private class EmptyBasicFileAttributes : BasicFileAttributes {
        companion object {
            val INSTANCE = EmptyBasicFileAttributes()
        }

        override fun lastModifiedTime(): FileTime {
            TODO("Not yet implemented")
        }

        override fun lastAccessTime(): FileTime {
            TODO("Not yet implemented")
        }

        override fun creationTime(): FileTime {
            TODO("Not yet implemented")
        }

        override fun isRegularFile(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isDirectory(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isSymbolicLink(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isOther(): Boolean {
            TODO("Not yet implemented")
        }

        override fun size(): Long {
            TODO("Not yet implemented")
        }

        override fun fileKey(): Any {
            TODO("Not yet implemented")
        }

    }

}