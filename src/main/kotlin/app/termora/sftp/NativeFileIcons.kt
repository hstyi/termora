package app.termora.sftp

import app.termora.Application
import app.termora.I18n
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeLeafIcon
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.SystemUtils
import org.eclipse.jgit.util.LRUMap
import java.util.*
import javax.swing.Icon
import javax.swing.filechooser.FileSystemView.getFileSystemView

object NativeFileIcons {

    /**
     * key: filename , value: <icon,description>
     */
    private val cache = LRUMap<String, Pair<Icon, String>>(16, 512)
    private val folderIcon = FlatTreeClosedIcon()
    private val fileIcon = FlatTreeLeafIcon()

    init {
        if (SystemUtils.IS_OS_UNIX) {
            cache[SystemUtils.USER_HOME] = Pair(FlatTreeClosedIcon(), I18n.getString("termora.folder"))
        }
    }

    fun getFolderIcon(): Icon {
        return getIcon(UUID.randomUUID().toString(), false).first
    }

    fun getFileIcon(filename: String): Icon {
        return getIcon(filename, true).first
    }

    fun getIcon(filename: String, isFile: Boolean = true): Pair<Icon, String> {
        if (isFile) {
            val extension = FilenameUtils.getExtension(filename)
            if (cache.containsKey(extension)) {
                return cache.getValue(extension)
            }
        } else {
            if (cache.containsKey(SystemUtils.USER_HOME)) {
                return cache.getValue(SystemUtils.USER_HOME)
            }
        }

        val isDirectory = !isFile

        if (SystemInfo.isWindows) {
            val file = if (isDirectory) FileUtils.getFile(SystemUtils.USER_HOME) else
                FileUtils.getFile(Application.getTemporaryDir(), "${UUID.randomUUID()}.${filename}")
            if (isFile && !file.exists()) {
                file.createNewFile()
            }
            val icon = getFileSystemView().getSystemIcon(file, 16, 16)
            val description = getFileSystemView().getSystemTypeDescription(file)
            val pair = icon to description

            if (isDirectory) {
                cache[SystemUtils.USER_HOME] = pair
            } else {
                cache[FilenameUtils.getExtension(file.name)] = pair
            }

            if (isFile) FileUtils.deleteQuietly(file)

            return pair
        }

        return Pair(
            if (isDirectory) folderIcon else fileIcon,
            if (isDirectory) I18n.getString("termora.folder") else FilenameUtils.getExtension(filename)
        )
    }


}