package app.termora.transport

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ExecutorService

class FileSystemProviderDelegate(val delegate: FileSystemProvider) : FileSystemProvider() {
    override fun getScheme(): String? {
        return delegate.scheme
    }

    override fun newFileSystem(
        uri: URI?,
        env: Map<String?, *>?
    ): FileSystem? {
        return delegate.newFileSystem(uri, env)
    }

    override fun newFileSystem(
        path: Path?,
        env: Map<String?, *>?
    ): FileSystem? {
        return delegate.newFileSystem(path, env)
    }

    override fun getFileSystem(uri: URI?): FileSystem? {
        return delegate.getFileSystem(uri)
    }

    override fun getPath(uri: URI): Path {
        return delegate.getPath(uri)
    }

    override fun newInputStream(
        path: Path?,
        vararg options: OpenOption?
    ): InputStream? {
        return delegate.newInputStream(path, *options)
    }

    override fun newOutputStream(
        path: Path?,
        vararg options: OpenOption?
    ): OutputStream? {
        return delegate.newOutputStream(path, *options)
    }

    override fun newFileChannel(
        path: Path?,
        options: Set<OpenOption?>?,
        vararg attrs: FileAttribute<*>?
    ): FileChannel? {
        return delegate.newFileChannel(path, options, *attrs)
    }

    override fun newAsynchronousFileChannel(
        path: Path?,
        options: Set<OpenOption?>?,
        executor: ExecutorService?,
        vararg attrs: FileAttribute<*>?
    ): AsynchronousFileChannel? {
        return delegate.newAsynchronousFileChannel(path, options, executor, *attrs)
    }

    override fun newByteChannel(
        path: Path?,
        options: Set<OpenOption?>?,
        vararg attrs: FileAttribute<*>?
    ): SeekableByteChannel? {
        return delegate.newByteChannel(path, options, *attrs)
    }

    override fun newDirectoryStream(
        dir: Path?,
        filter: DirectoryStream.Filter<in Path>?
    ): DirectoryStream<Path?>? {
        return delegate.newDirectoryStream(dir, filter)
    }

    override fun createDirectory(dir: Path?, vararg attrs: FileAttribute<*>?) {
        return delegate.createDirectory(dir, *attrs)
    }

    override fun createSymbolicLink(
        link: Path?,
        target: Path?,
        vararg attrs: FileAttribute<*>?
    ) {
        return delegate.createSymbolicLink(link, target, *attrs)
    }

    override fun createLink(link: Path?, existing: Path?) {
        return delegate.createLink(link, existing)
    }

    override fun delete(path: Path?) {
        return delegate.delete(path)
    }

    override fun deleteIfExists(path: Path?): Boolean {
        return delegate.deleteIfExists(path)
    }

    override fun readSymbolicLink(link: Path?): Path? {
        return delegate.readSymbolicLink(link)
    }

    override fun copy(
        source: Path?,
        target: Path?,
        vararg options: CopyOption?
    ) {
        delegate.copy(source, target, *options)
    }

    override fun move(
        source: Path?,
        target: Path?,
        vararg options: CopyOption?
    ) {
        delegate.move(source, target, *options)
    }

    override fun isSameFile(path: Path?, path2: Path?): Boolean {
return        delegate.isSameFile(path, path2)
    }

    override fun isHidden(path: Path?): Boolean {
       return  delegate.isHidden(path)
    }

    override fun getFileStore(path: Path?): FileStore? {
        return delegate.getFileStore(path)
    }

    override fun checkAccess(path: Path?, vararg modes: AccessMode?) {
         delegate.checkAccess(path, *modes)
    }

    override fun <V : FileAttributeView?> getFileAttributeView(
        path: Path?,
        type: Class<V?>?,
        vararg options: LinkOption?
    ): V? {
        return delegate.getFileAttributeView(path, type, *options)
    }

    override fun <A : BasicFileAttributes?> readAttributes(
        path: Path?,
        type: Class<A?>?,
        vararg options: LinkOption?
    ): A? {
        TODO("Not yet implemented")
    }

    override fun readAttributes(
        path: Path?,
        attributes: String?,
        vararg options: LinkOption?
    ): Map<String?, Any?>? {
        TODO("Not yet implemented")
    }

    override fun setAttribute(
        path: Path?,
        attribute: String?,
        value: Any?,
        vararg options: LinkOption?
    ) {
        TODO("Not yet implemented")
    }

    override fun exists(path: Path?, vararg options: LinkOption?): Boolean {
        return super.exists(path, *options)
    }

    override fun <A : BasicFileAttributes?> readAttributesIfExists(
        path: Path?,
        type: Class<A?>?,
        vararg options: LinkOption?
    ): A? {
        return super.readAttributesIfExists(path, type, *options)
    }
}