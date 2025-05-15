package app.termora.plugins.s3

import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.test.Test

@Testcontainers
class S3FileProviderTest {

    @Container
    private val monio: GenericContainer<*> = GenericContainer("minio/minio")
        .withEnv("MINIO_ACCESS_KEY", minioAccessKey)
        .withEnv("MINIO_SECRET_KEY", minioSecretKey)
        .withExposedPorts(9000, 9090)
        .withCommand("server", "/data", "--console-address", ":9090", "-address", ":9000")

    companion object {

    }

    @Test
    fun test() {

        val minioClient = MinioClient.builder()
            .endpoint("http://127.0.0.1:${monio.getMappedPort(9000)}")
            .credentials(minioAccessKey, minioSecretKey)
            .build()
        minioClient.makeBucket(MakeBucketArgs.builder().bucket("bucket").build())

        minioClient.putObject(
            PutObjectArgs.builder().bucket("bucket").`object`("test.txt")
                .stream(ByteArrayInputStream("test".toByteArray()), -1, 1024 * 1024 * 5).build()
        )

        minioClient.putObject(
            PutObjectArgs.builder().bucket("bucket").`object`("folder1/test.txt")
                .stream(ByteArrayInputStream("test".toByteArray()), -1, 1024 * 1024 * 5).build()
        )

        minioClient.putObject(
            PutObjectArgs.builder().bucket("bucket").`object`("folder2/test.txt")
                .stream(ByteArrayInputStream("test".toByteArray()), -1, 1024 * 1024 * 5).build()
        )

    }
}