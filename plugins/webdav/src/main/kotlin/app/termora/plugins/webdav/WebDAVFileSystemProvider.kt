package app.termora.plugins.webdav

import app.termora.Application
import app.termora.ResponseException
import app.termora.transfer.s3.S3FileSystemProvider
import app.termora.transfer.s3.S3Path
import com.github.sardine.Sardine
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.apache.commons.io.IOUtils
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import java.nio.file.AccessMode
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

class WebDAVFileSystemProvider(
    private val sardine: Sardine,
    private val endpoint: String,
    private val authorization: String,
) : S3FileSystemProvider() {


    override fun getScheme(): String? {
        return "webdav"
    }

    override fun getOutputStream(path: S3Path): OutputStream {
        return createStreamer(path)
    }

    override fun getInputStream(path: S3Path): InputStream {
        return sardine.get(getFullUrl(path))
    }

    private fun createStreamer(path: S3Path): OutputStream {
        val pis = PipedInputStream()
        val pos = PipedOutputStream(pis)
        val exception = AtomicReference<Throwable>()

        val thread = Thread.ofVirtual().start {
            try {
                val builder = Request.Builder()
                    .url("${endpoint}${path.absolutePathString()}")
                    .put(object : RequestBody() {
                        override fun contentType(): MediaType? {
                            return null
                        }

                        override fun contentLength(): Long {
                            return -1
                        }

                        override fun writeTo(sink: BufferedSink) {
                            pis.source().use { sink.writeAll(it) }
                        }

                    })

                if (authorization.isNotBlank())
                    builder.header("Authorization", authorization)

                // sardine 会重试，这里使用 okhttp
                val response = Application.httpClient.newCall(builder.build()).execute()
                IOUtils.closeQuietly(response)
                if (response.isSuccessful.not()) {
                    throw ResponseException(response.code, response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        val resources = sardine.list(getFullUrl(path))
        for (i in 1 until resources.size) {
            val resource = resources[i]
            val p = path.resolve(resource.name)
            p.attributes = p.attributes.copy(
                directory = resource.isDirectory,
                regularFile = resource.isDirectory.not(),
                size = resource.contentLength,
                lastModifiedTime = resource.modified.time,
            )
            paths.add(p)
        }
        return paths

    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        sardine.createDirectory(getFullUrl(dir))
    }

    override fun delete(path: S3Path, isDirectory: Boolean) {
        sardine.delete(getFullUrl(path))
    }

    override fun checkAccess(path: S3Path, vararg modes: AccessMode) {
        try {
            if (sardine.exists(getFullUrl(path)).not()) {
                throw NoSuchFileException(path.name)
            }
        } catch (e: Exception) {
            if (e is NoSuchFileException) throw e
            throw NoSuchFileException(e.message)
        }
    }

    private fun getFullUrl(path: Path): String {
        val pathname = URI(null, null, path.absolutePathString(), null).toString()
        return "${endpoint}${pathname}"
    }

}