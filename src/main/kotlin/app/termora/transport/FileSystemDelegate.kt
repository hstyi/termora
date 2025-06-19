package app.termora.transport

import java.nio.file.*
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

abstract class FileSystemDelegate(val delegate: FileSystem) : FileSystem() {

    override fun provider(): FileSystemProvider {
        return delegate.provider()
    }

    override fun close() {
        delegate.close()
    }

    override fun isOpen(): Boolean {
        return delegate.isOpen
    }

    override fun isReadOnly(): Boolean {
        return delegate.isReadOnly
    }

    override fun getSeparator(): String {
        return delegate.separator
    }

    override fun getRootDirectories(): Iterable<Path> {
        return delegate.rootDirectories
    }

    override fun getFileStores(): Iterable<FileStore> {
        return delegate.fileStores
    }

    override fun supportedFileAttributeViews(): Set<String> {
        return delegate.supportedFileAttributeViews()
    }

    override fun getPath(first: String, vararg more: String?): Path {
        return delegate.getPath(first, *more)
    }

    override fun getPathMatcher(syntaxAndPattern: String?): PathMatcher {
        return delegate.getPathMatcher(syntaxAndPattern)
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        return delegate.userPrincipalLookupService
    }

    override fun newWatchService(): WatchService {
        return delegate.newWatchService()
    }
}