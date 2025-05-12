package app.termora.vfs2

import org.apache.commons.vfs2.FileObject
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

object VFSWalker {
    fun walk(
        dir: FileObject,
        visitor: FileVisitor<FileObject>,
    ): FileVisitResult {

        // clear cache
        if (visitor.preVisitDirectory(dir, EmptyBasicFileAttributes.INSTANCE) == FileVisitResult.TERMINATE) {
            return FileVisitResult.TERMINATE
        }

        for (e in dir.children) {
            if (e.name.baseName == ".." || e.name.baseName == ".") continue
            if (e.isFolder) {
                if (walk(dir.resolveFile(e.name.baseName), visitor) == FileVisitResult.TERMINATE) {
                    return FileVisitResult.TERMINATE
                }
            } else {
                val result = visitor.visitFile(
                    dir.resolveFile(e.name.baseName),
                    EmptyBasicFileAttributes.INSTANCE
                )
                if (result == FileVisitResult.TERMINATE) {
                    return FileVisitResult.TERMINATE
                } else if (result == FileVisitResult.SKIP_SUBTREE) {
                    break
                }
            }
        }

        if (visitor.postVisitDirectory(dir, null) == FileVisitResult.TERMINATE) {
            return FileVisitResult.TERMINATE
        }

        return FileVisitResult.CONTINUE
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