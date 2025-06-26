package app.termora.plugins.s3

import io.minio.*
import io.minio.errors.ErrorResponseException
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.*
import java.nio.file.attribute.*
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

class S3FileSystemProvider(private val minioClient: MinioClient) : FileSystemProvider() {

    /**
     * 因为 S3 协议不存在文件夹，所以用户新建的文件夹先保存到内存中
     */
    private val directories = mutableMapOf<String, MutableList<S3Path>>()

    override fun getScheme(): String? {
        return "s3"
    }

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
        if (options.contains(StandardOpenOption.WRITE)) {
            return S3WriteSeekableByteChannel(Channels.newChannel(createStreamer(path)))
        } else {
            val response = minioClient.getObject(
                GetObjectArgs.builder().bucket(path.bucketName)
                    .`object`(path.objectName).build()
            )
            return S3ReadSeekableByteChannel(Channels.newChannel(response), stat(path))
        }
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

    override fun newDirectoryStream(
        dir: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        return object : DirectoryStream<Path> {
            override fun iterator(): MutableIterator<Path> {
                return files(dir as S3Path).iterator()
            }

            override fun close() {
            }

        }
    }

    private fun files(path: S3Path): MutableList<S3Path> {
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
            return
        }
        minioClient.removeObject(
            RemoveObjectArgs.builder().bucket(path.bucketName).`object`(path.objectName).build()
        )
    }

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

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        if (path !is S3Path) throw UnsupportedOperationException("path must be a S3Path")

        try {
            stat(path)
        } catch (e: ErrorResponseException) {
            throw NoSuchFileException(e.errorResponse().message())
        }
    }


    private fun stat(path: S3Path): StatObjectResponse {
        return minioClient.statObject(
            StatObjectArgs.builder()
                .`object`(path.objectName)
                .bucket(path.bucketName).build()
        )
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