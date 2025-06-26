package app.termora.plugins.s3

import app.termora.randomUUID
import app.termora.transfer.PathWalker
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.apache.commons.io.file.PathVisitor
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test

@Testcontainers
class S3FileSystemTest {

    private val ak = randomUUID()
    private val sk = randomUUID()

    @Container
    private val monio: GenericContainer<*> = GenericContainer("minio/minio")
        .withEnv("MINIO_ACCESS_KEY", ak)
        .withEnv("MINIO_SECRET_KEY", sk)
        .withExposedPorts(9000, 9090)
        .withCommand("server", "/data", "--console-address", ":9090", "-address", ":9000")

    companion object {

    }

    @Test
    fun test() {
        val endpoint = "http://127.0.0.1:${monio.getMappedPort(9000)}"
        val minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(ak, sk)
            .build()

        for (i in 0 until 1) {
            val bucket = "bucket${i}"
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())

            for (n in 0 until 1) {
                minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucket)
                        .`object`("test1/test2/test3/file${n}")
                        .stream(ByteArrayInputStream("Hello 中国".toByteArray()), -1, 5 * 1024 * 1024)
                        .build()
                )
            }
        }

        val fileSystem = MyS3FileSystem(minioClient)
        val path = fileSystem.getPath("/")
        PathWalker.walkFileTree(path, object : PathVisitor {
            override fun preVisitDirectory(
                dir: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                println("preVisitDirectory: ${dir.absolutePathString()}")
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(
                file: Path,
                attrs: BasicFileAttributes
            ): FileVisitResult {
                println(file.readText())
                file.writeText("test")
                println(file.readText())
                println("visitFile: ${file.absolutePathString()}")
                return FileVisitResult.CONTINUE

            }

            override fun visitFileFailed(
                file: Path?,
                exc: IOException
            ): FileVisitResult {
                return FileVisitResult.TERMINATE

            }

            override fun postVisitDirectory(
                dir: Path,
                exc: IOException?
            ): FileVisitResult {
                println("postVisitDirectory: ${dir.absolutePathString()}")
                return FileVisitResult.CONTINUE
            }

        })

    }
}