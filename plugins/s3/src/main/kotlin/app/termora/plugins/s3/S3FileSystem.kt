package app.termora.plugins.s3

import io.minio.MinioClient
import org.apache.sshd.common.file.util.BaseFileSystem
import java.nio.file.Path
import java.nio.file.attribute.UserPrincipalLookupService
import java.util.concurrent.atomic.AtomicBoolean

class S3FileSystem(
    private val minioClient: MinioClient,
) : BaseFileSystem<S3Path>(S3FileSystemProvider(minioClient)) {

    private val isOpen = AtomicBoolean(true)

    override fun create(root: String?, names: List<String>): S3Path {
        val path = S3Path(this, root, names)
        if (names.isEmpty()) {
            path.attributes = path.attributes.copy(directory = true)
        }
        return path
    }

    override fun close() {
        if (isOpen.compareAndSet(false, true)) {
            minioClient.close()
        }
    }

    override fun isOpen(): Boolean {
        return isOpen.get()
    }

    override fun getRootDirectories(): Iterable<Path> {
        return mutableSetOf<Path>(create(separator))
    }

    override fun supportedFileAttributeViews(): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }
}
