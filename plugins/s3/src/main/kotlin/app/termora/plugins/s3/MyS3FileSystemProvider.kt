package app.termora.plugins.s3

import app.termora.transfer.s3.S3FileAttributes
import app.termora.transfer.s3.S3FileSystemProvider
import app.termora.transfer.s3.S3Path
import io.minio.*
import io.minio.errors.ErrorResponseException
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.AccessMode
import java.nio.file.NoSuchFileException
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString

class MyS3FileSystemProvider(private val minioClient: MinioClient) : S3FileSystemProvider() {

    override fun getScheme(): String? {
        return "s3"
    }

    override fun getOutputStream(path: S3Path): OutputStream {
        return createStreamer(path)
    }

    override fun getInputStream(path: S3Path): InputStream {
        return minioClient.getObject(
            GetObjectArgs.builder().bucket(path.bucketName)
                .`object`(path.objectName).build()
        )
    }


    private fun createStreamer(path: S3Path): OutputStream {
        val pis = PipedInputStream()
        val pos = PipedOutputStream(pis)
        val exception = AtomicReference<Throwable>()

        val thread = Thread.ofVirtual().start {
            try {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(path.bucketName)
                        .stream(pis, -1, 32 * 1024 * 1024)
                        .`object`(path.objectName).build()
                )
            } catch (e: Exception) {
                exception.set(e)
            } finally {
                IOUtils.closeQuietly(pis)
            }
        }

        return object : OutputStream() {
            override fun write(b: Int) {
                val exception = exception.get()
                if (exception != null) throw exception
                pos.write(b)
            }

            override fun close() {
                pos.close()
                if (thread.isAlive) thread.join()
            }
        }
    }

    override fun fetchChildren(path: S3Path): MutableList<S3Path> {
        val paths = mutableListOf<S3Path>()

        // root
        if (path.isRoot) {
            for (bucket in minioClient.listBuckets()) {
                val p = path.resolve(bucket.name())
                p.attributes = S3FileAttributes(
                    directory = true,
                    lastModifiedTime = bucket.creationDate().toInstant().toEpochMilli()
                )
                paths.add(p)
            }
            return paths
        }

        var startAfter = StringUtils.EMPTY
        val maxKeys = 100

        while (true) {
            val builder = ListObjectsArgs.builder()
                .bucket(path.bucketName)
                .maxKeys(maxKeys)
                .delimiter(path.fileSystem.separator)

            if (path.objectName.isNotBlank()) builder.prefix(path.objectName + path.fileSystem.separator)
            if (startAfter.isNotBlank()) builder.startAfter(startAfter)


            val subPaths = mutableListOf<S3Path>()
            for (e in minioClient.listObjects(builder.build())) {
                val item = e.get()
                val p = path.bucket.resolve(item.objectName())
                var attributes = p.attributes.copy(
                    directory = item.isDir,
                    regularFile = item.isDir.not(),
                    size = item.size()
                )
                if (item.lastModified() != null) {
                    attributes = attributes.copy(lastModifiedTime = item.lastModified().toInstant().toEpochMilli())
                }
                p.attributes = attributes

                // 如果是文件夹，那么就要删除内存中的
                if (attributes.isDirectory) {
                    delete(p)
                }

                subPaths.add(p)
                startAfter = item.objectName()
            }

            paths.addAll(subPaths)

            if (subPaths.size < maxKeys)
                break


        }

        paths.addAll(directories[path.absolutePathString()] ?: emptyList())

        return paths
    }


    override fun delete(path: S3Path, isDirectory: Boolean) {
        if (isDirectory.not())
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(path.bucketName).`object`(path.objectName).build()
            )
    }


    override fun checkAccess(path: S3Path, vararg modes: AccessMode) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .`object`(path.objectName)
                    .bucket(path.bucketName).build()
            )
        } catch (e: ErrorResponseException) {
            throw NoSuchFileException(e.errorResponse().message())
        }
    }

}