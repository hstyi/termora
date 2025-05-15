package app.termora.plugins.s3

import io.minio.MinioClient
import org.apache.commons.vfs2.FileType
import org.apache.commons.vfs2.provider.AbstractFileName
import org.apache.commons.vfs2.provider.AbstractFileObject

class S3FileObject(
    private val minio: MinioClient,
    fileName: AbstractFileName,
    fileSystem: S3FileSystem
) : AbstractFileObject<S3FileSystem>(fileName, fileSystem) {
    override fun doGetContentSize(): Long {
        return 0
    }

    override fun doGetType(): FileType {
        return FileType.IMAGINARY
    }

    override fun doListChildren(): Array<out String?>? {
        return null
    }
}