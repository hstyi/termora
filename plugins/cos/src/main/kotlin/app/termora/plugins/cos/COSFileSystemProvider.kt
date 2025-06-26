package app.termora.plugins.cos

import app.termora.transfer.s3.S3FileAttributes
import app.termora.transfer.s3.S3FileSystemProvider
import app.termora.transfer.s3.S3Path
import com.qcloud.cos.model.ListObjectsRequest
import com.qcloud.cos.model.ObjectMetadata
import com.qcloud.cos.model.PutObjectRequest
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

class COSFileSystemProvider(private val clientHandler: COSClientHandler) : S3FileSystemProvider() {


    override fun getScheme(): String? {
        return "s3"
    }

    override fun getOutputStream(path: S3Path): OutputStream {
        return createStreamer(path)
    }

    override fun getInputStream(path: S3Path): InputStream {
        val client = clientHandler.getClientForBucket(path.bucketName)
        return client.getObject(path.bucketName, path.objectName).objectContent
    }

    private fun createStreamer(path: S3Path): OutputStream {
        val pis = PipedInputStream()
        val pos = PipedOutputStream(pis)
        val exception = AtomicReference<Throwable>()

        val thread = Thread.ofVirtual().start {
            try {
                val client = clientHandler.getClientForBucket(path.bucketName)
                client.putObject(PutObjectRequest(path.bucketName, path.objectName, pis, ObjectMetadata()))
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
            for (bucket in clientHandler.buckets) {
                val p = path.resolve(bucket.name)
                p.attributes = S3FileAttributes(
                    directory = true,
                    lastModifiedTime = bucket.creationDate.toInstant().toEpochMilli()
                )
                paths.add(p)
            }
            return paths
        }

        var nextMarker = StringUtils.EMPTY
        val maxKeys = 100
        val bucketName = path.bucketName
        while (true) {
            val request = ListObjectsRequest()
                .withBucketName(bucketName)
                .withMaxKeys(maxKeys)
                .withDelimiter(path.fileSystem.separator)

            if (path.objectName.isNotBlank()) request.withPrefix(path.objectName + path.fileSystem.separator)
            if (nextMarker.isNotBlank()) request.withMarker(nextMarker)


            val objectListing = clientHandler.getClientForBucket(bucketName).listObjects(request)
            for (e in objectListing.commonPrefixes) {
                val p = path.bucket.resolve(e)
                p.attributes = p.attributes.copy(directory = true)
                delete(p)
                paths.add(p)
            }

            for (e in objectListing.objectSummaries) {
                val p = path.bucket.resolve(e.key)
                p.attributes = p.attributes.copy(
                    regularFile = true, size = e.size,
                    lastModifiedTime = e.lastModified.time
                )
                paths.add(p)
            }

            if (objectListing.isTruncated.not()) {
                break
            }

            nextMarker = objectListing.nextMarker

        }

        paths.addAll(directories[path.absolutePathString()] ?: emptyList())

        return paths
    }

    override fun delete(path: S3Path, isDirectory: Boolean) {
        if (isDirectory.not())
            clientHandler.getClientForBucket(path.bucketName).deleteObject(path.bucketName, path.objectName)
    }

    override fun checkAccess(path: S3Path, vararg modes: AccessMode) {
        try {
            val client = clientHandler.getClientForBucket(path.bucketName)
            if (client.doesObjectExist(path.bucketName, path.objectName).not()) {
                throw NoSuchFileException(path.objectName)
            }
        } catch (e: Exception) {
            if (e is NoSuchFileException) throw e
            throw NoSuchFileException(e.message)
        }
    }

}