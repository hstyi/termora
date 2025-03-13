package app.termora.sftp

import app.termora.I18n
import app.termora.NativeStringComparator
import app.termora.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.apache.sshd.sftp.client.fs.SftpPath
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.*
import javax.swing.table.DefaultTableModel
import kotlin.io.path.*

class FileSystemViewTableModel : DefaultTableModel() {


    companion object {
        const val COLUMN_NAME = 0
        const val COLUMN_TYPE = 1
        const val COLUMN_FILE_SIZE = 2
        const val COLUMN_LAST_MODIFIED_TIME = 3
        const val COLUMN_ATTRS = 4
        const val COLUMN_OWNER = 5
        private val log = LoggerFactory.getLogger(FileSystemViewTableModel::class.java)

        private fun fromSftpPermissions(sftpPermissions: Int): Set<PosixFilePermission> {
            val result = mutableSetOf<PosixFilePermission>()

            // 将十进制权限转换为八进制字符串
            val octalPermissions = sftpPermissions.toString(8)

            // 仅取后三位权限部分
            if (octalPermissions.length < 3) {
                return result
            }

            val permissionBits = octalPermissions.takeLast(3)

            // 解析每一部分的权限
            val owner = permissionBits[0].digitToInt()
            val group = permissionBits[1].digitToInt()
            val others = permissionBits[2].digitToInt()

            // 处理所有者权限
            if ((owner and 4) != 0) result.add(PosixFilePermission.OWNER_READ)
            if ((owner and 2) != 0) result.add(PosixFilePermission.OWNER_WRITE)
            if ((owner and 1) != 0) result.add(PosixFilePermission.OWNER_EXECUTE)

            // 处理组权限
            if ((group and 4) != 0) result.add(PosixFilePermission.GROUP_READ)
            if ((group and 2) != 0) result.add(PosixFilePermission.GROUP_WRITE)
            if ((group and 1) != 0) result.add(PosixFilePermission.GROUP_EXECUTE)

            // 处理其他用户权限
            if ((others and 4) != 0) result.add(PosixFilePermission.OTHERS_READ)
            if ((others and 2) != 0) result.add(PosixFilePermission.OTHERS_WRITE)
            if ((others and 1) != 0) result.add(PosixFilePermission.OTHERS_EXECUTE)

            return result
        }
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val attr = getAttr(row)
        return when (column) {
            COLUMN_NAME -> attr.name
            COLUMN_FILE_SIZE -> if (attr.isDirectory) StringUtils.EMPTY else formatBytes(attr.size)
            COLUMN_TYPE -> attr.type
            COLUMN_LAST_MODIFIED_TIME -> if (attr.modified > 0) DateFormatUtils.format(
                Date(attr.modified),
                "yyyy/MM/dd HH:mm"
            ) else StringUtils.EMPTY

            COLUMN_ATTRS -> attr.permissions
            COLUMN_OWNER -> attr.owner
            else -> StringUtils.EMPTY
        }
    }

    override fun getDataVector(): Vector<Vector<Any>> {
        return super.getDataVector()
    }

    override fun getColumnCount(): Int {
        return 6
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            COLUMN_NAME -> String::class.java
            else -> super.getColumnClass(columnIndex)
        }
    }

    fun getAttr(row: Int): Attr {
        return super.getValueAt(row, 0) as Attr
    }

    fun getPathNames(): Set<String> {
        val names = linkedSetOf<String>()
        for (i in 0 until rowCount) {
            names.add(getAttr(i).name)
        }
        return names
    }


    override fun getColumnName(column: Int): String {
        return when (column) {
            COLUMN_NAME -> I18n.getString("termora.transport.table.filename")
            COLUMN_FILE_SIZE -> I18n.getString("termora.transport.table.size")
            COLUMN_TYPE -> I18n.getString("termora.transport.table.type")
            COLUMN_LAST_MODIFIED_TIME -> I18n.getString("termora.transport.table.modified-time")
            COLUMN_ATTRS -> I18n.getString("termora.transport.table.permissions")
            COLUMN_OWNER -> I18n.getString("termora.transport.table.owner")
            else -> StringUtils.EMPTY
        }
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    suspend fun reload(dir: Path, useFileHiding: Boolean) {

        if (log.isDebugEnabled) {
            log.debug("Reloading {} , useFileHiding {}", dir, useFileHiding)
        }

        val attrs = mutableListOf<Attr>()
        if (dir.parent != null) {
            attrs.add(ParentAttr(dir.parent))
        }

        withContext(Dispatchers.IO) {
            Files.list(dir).use { paths ->
                for (path in paths) {
                    val attr = if (path is SftpPath) SftpAttr(path) else Attr(path)
                    if (useFileHiding && attr.isHidden) continue
                    attrs.add(attr)
                }
            }
        }

        attrs.sortWith(compareBy<Attr> { !it.isDirectory }.thenComparing { a, b ->
            NativeStringComparator.getInstance().compare(
                a.name,
                b.name
            )
        })

        withContext(Dispatchers.Swing) {
            while (rowCount > 0) removeRow(0)
            attrs.forEach { addRow(arrayOf(it)) }
        }

    }


    open class Attr(val path: Path) {

        /**
         * 名称
         */
        open val name by lazy { path.name }

        /**
         * 文件类型
         */
        open val type by lazy {
            if (path.fileSystem.isWindows()) NativeFileIcons.getIcon(name, isFile).second
            else if (isSymbolicLink) I18n.getString("termora.transport.table.type.symbolic-link")
            else NativeFileIcons.getIcon(name, isFile).second
        }

        /**
         * 大小
         */
        open val size by lazy { path.fileSize() }

        /**
         * 修改时间
         */
        open val modified by lazy { path.getLastModifiedTime().toMillis() }

        /**
         * 获取所有者
         */
        open val owner by lazy { StringUtils.EMPTY }

        /**
         * 获取操作系统图标
         */
        open val icon by lazy { NativeFileIcons.getIcon(name, isFile).first }

        /**
         * 是否是文件夹
         */
        open val isDirectory by lazy { path.isDirectory() }

        /**
         * 是否是文件
         */
        open val isFile by lazy { !isDirectory }

        /**
         * 是否是文件夹
         */
        open val isHidden by lazy { path.isHidden() }

        open val isSymbolicLink by lazy { path.isSymbolicLink() }

        /**
         * 获取权限
         */
        open val permissions: String by lazy {
            posixFilePermissions.let {
                if (it.isNotEmpty()) PosixFilePermissions.toString(
                    it
                ) else StringUtils.EMPTY
            }
        }
        open val posixFilePermissions by lazy { if (path.fileSystem.isUnix()) path.getPosixFilePermissions() else emptySet() }

        open fun toFile(): File {
            if (path.fileSystem.isSFTP()) {
                return File(path.absolutePathString())
            }
            return path.toFile()
        }
    }

    class ParentAttr(path: Path) : Attr(path) {
        override val name by lazy { ".." }
        override val isDirectory = true
        override val isFile = false
        override val isHidden = false
        override val permissions = StringUtils.EMPTY
        override val modified = 0L
        override val type = StringUtils.EMPTY
        override val icon by lazy { NativeFileIcons.getFolderIcon() }
        override val isSymbolicLink = false

    }


    class SftpAttr(sftpPath: SftpPath) : Attr(sftpPath) {
        private val attributes = sftpPath.attributes

        override val isSymbolicLink = attributes.isSymbolicLink
        override val isDirectory = if (isSymbolicLink) sftpPath.isDirectory() else attributes.isDirectory
        override val isHidden = name.startsWith(".")
        override val size = attributes.size
        override val owner: String = StringUtils.defaultString(attributes.owner)
        override val modified = attributes.modifyTime.toMillis()
        override val permissions: String = PosixFilePermissions.toString(fromSftpPermissions(attributes.permissions))
        override val posixFilePermissions = fromSftpPermissions(attributes.permissions)

        override fun toFile(): File {
            return File(path.absolutePathString())
        }
    }


}