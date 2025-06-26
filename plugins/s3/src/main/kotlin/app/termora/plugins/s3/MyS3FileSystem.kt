package app.termora.plugins.s3

import app.termora.transfer.s3.S3FileSystem
import io.minio.MinioClient

class MyS3FileSystem(private val minioClient: MinioClient) :
    S3FileSystem(MyS3FileSystemProvider(minioClient)) {

    override fun close() {
        minioClient.close()
        super.close()
    }
}
