package app.termora.sftp

import app.termora.Application
import app.termora.I18n
import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeLeafIcon
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
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
            cache[SystemUtils.USER_HOME] = Pair(folderIcon, I18n.getString("termora.folder"))
        }
    }

    fun getFolderIcon(): Icon {
        return getIcon(UUID.randomUUID().toString(), false).first
    }

    fun getFileIcon(filename: String): Icon {
        return getIcon(filename, true).first
    }

    fun getIcon(filename: String, isFile: Boolean = true, width: Int = 16, height: Int = 16): Pair<Icon, String> {
        val key = if (isFile) FilenameUtils.getExtension(filename) + "." + width + "@" + height
        else SystemUtils.USER_HOME + "." + width + "@" + height

        if (cache.containsKey(key)) {
            return cache.getValue(key)
        }

        val isDirectory = !isFile

        if (SystemInfo.isWindows) {

            val file = if (isDirectory) FileUtils.getFile(SystemUtils.USER_HOME) else
                FileUtils.getFile(Application.getTemporaryDir(), "${UUID.randomUUID()}.${filename}")
            if (isFile && !file.exists()) {
                file.createNewFile()
            }

            val icon = getFileSystemView().getSystemIcon(file, width, height) ?: if (isFile) fileIcon else folderIcon
            val description = getFileSystemView().getSystemTypeDescription(file)
                ?: StringUtils.defaultString(file.extension)
            val pair = icon to description

            cache[key] = pair

            if (isFile) FileUtils.deleteQuietly(file)

            return pair
        }

        return Pair(
            if (isDirectory) folderIcon else fileIcon,
            if (isDirectory) I18n.getString("termora.folder") else FilenameUtils.getExtension(filename)
        )
    }


}