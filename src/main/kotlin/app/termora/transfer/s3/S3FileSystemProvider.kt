package app.termora.transfer.s3

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.*
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

abstract class S3FileSystemProvider() : FileSystemProvider() {

    /**
     * 因为 S3 协议不存在文件夹，所以用户新建的文件夹先保存到内存中
     */
    protected val directories = mutableMapOf<String, MutableList<S3Path>>()

    override fun newFileSystem(
        uri: URI,
        env: Map<String, *>
    ): FileSystem {
        TODO("Not yet implemented")
    }

    override fun getFileSystem(uri: URI): FileSystem {
        TODO("Not yet implemented")
    }

    override fun getPath(uri: URI): Path {
        TODO("Not yet implemented")
    }

    override fun newByteChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attrs: FileAttribute<*>
    ): SeekableByteChannel {
        if (path !is S3Path) throw UnsupportedOperationException("path must be a S3Path")
        return if (options.contains(StandardOpenOption.WRITE)) {
            S3WriteSeekableByteChannel(getOutputStream(path))
        } else {
            S3ReadSeekableByteChannel(getInputStream(path), path.attributes.size())
        }
    }

    abstract fun getOutputStream(path: S3Path): OutputStream
    abstract fun getInputStream(path: S3Path): InputStream

    override fun newDirectoryStream(
        dir: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        return object : DirectoryStream<Path> {
            override fun iterator(): MutableIterator<Path> {
                return fetchChildren(dir as S3Path).iterator()
            }

            override fun close() {
            }

        }
    }

    abstract fun fetchChildren(path: S3Path): MutableList<S3Path>


    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        synchronized(this) {
            if (dir !is S3Path) throw UnsupportedOperationException("dir must be a S3Path")
            if (dir.isRoot || dir.isBucket) throw UnsupportedOperationException("No operation permission")
            val parent = dir.parent ?: throw UnsupportedOperationException("No operation permission")
            directories.computeIfAbsent(parent.absolutePathString()) { mutableListOf() }
                .add(dir.apply {
                    attributes = attributes.copy(directory = true, lastModifiedTime = System.currentTimeMillis())
                })
        }
    }

    override fun delete(path: Path) {
        if (path !is S3Path) throw UnsupportedOperationException("path must be a S3Path")
        if (path.attributes.isDirectory) {
            val parent = path.parent
            if (parent != null) {
                synchronized(this) {
                    directories[parent.absolutePathString()]?.removeIf { it.name == path.name }
                }
            }
            delete(path, true)
        } else {
            delete(path, false)
        }
    }

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        if (path !is S3Path) throw UnsupportedOperationException("path must be a S3Path")
        checkAccess(path, *modes)
    }

    abstract fun delete(path: S3Path, isDirectory: Boolean)
    abstract fun checkAccess(path: S3Path, vararg modes: AccessMode)

    override fun copy(
        source: Path?,
        target: Path?,
        vararg options: CopyOption?
    ) {
        throw UnsupportedOperationException()
    }

    override fun move(
        source: Path?,
        target: Path?,
        vararg options: CopyOption?
    ) {
        throw UnsupportedOperationException()
    }

    override fun isSameFile(path: Path?, path2: Path?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun isHidden(path: Path?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun getFileStore(path: Path?): FileStore? {
        throw UnsupportedOperationException()
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption?
    ): V {
        if (path is S3Path) {
            return type.cast(object : BasicFileAttributeView {
                override fun name(): String {
                    return "basic"
                }

                override fun readAttributes(): BasicFileAttributes {
                    return path.attributes
                }

                override fun setTimes(
                    lastModifiedTime: FileTime?,
                    lastAccessTime: FileTime?,
                    createTime: FileTime?
                ) {
                    throw UnsupportedOperationException()
                }

            })
        }
        throw UnsupportedOperationException()
    }

    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        if (path is S3Path) {
            return type.cast(getFileAttributeView(path, BasicFileAttributeView::class.java).readAttributes())
        }
        throw UnsupportedOperationException()
    }

    override fun readAttributes(
        path: Path?,
        attributes: String?,
        vararg options: LinkOption?
    ): Map<String?, Any?>? {
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path?,
        attribute: String?,
        value: Any?,
        vararg options: LinkOption?
    ) {
        throw UnsupportedOperationException()
    }
}