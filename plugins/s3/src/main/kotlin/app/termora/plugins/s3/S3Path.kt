package app.termora.plugins.s3

import org.apache.sshd.common.file.util.BasePath
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class S3Path(
    fileSystem: S3FileSystem,
    root: String?,
    names: List<String>,
) : BasePath<S3Path, S3FileSystem>(fileSystem, root, names) {


    private val separator get() = fileSystem.separator

    var attributes = S3FileAttributes()

    /**
     * 是否是 Bucket
     */
    val isBucket get() = parent != null && parent?.parent == null

    /**
     * 是否是根
     */
    val isRoot get() = absolutePathString() == separator

    /**
     * Bucket Name
     */
    val bucketName: String get() = names.first()

    /**
     * 获取 Bucket
     */
    val bucket: S3Path get() = fileSystem.getPath(root, bucketName)

    /**
     * 获取所在 Bucket 的路径
     */
    val objectName: String get() = names.subList(1, names.size).joinToString(separator)

    override fun toRealPath(vararg options: LinkOption): Path {
        return toAbsolutePath()
    }

    override fun getParent(): S3Path? {
        val path = super.getParent() ?: return null
        path.attributes = path.attributes.copy(directory = true)
        return path
    }

}