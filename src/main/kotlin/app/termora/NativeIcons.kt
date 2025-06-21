package app.termora

import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeLeafIcon
import org.apache.commons.lang3.SystemUtils
import java.nio.file.Files
import javax.swing.Icon
import javax.swing.UIManager
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.createTempFile

object NativeIcons {


    val folderIcon: Icon = if (SystemUtils.IS_OS_LINUX) FlatTreeClosedIcon()
    else if (SystemUtils.IS_OS_MAC_OSX)
        UIManager.getIcon("FileView.directoryIcon") ?: FlatTreeClosedIcon()
    else if (SystemUtils.IS_OS_WINDOWS)
        FileSystemView.getFileSystemView().getSystemIcon(SystemUtils.getUserDir()) ?: FlatTreeClosedIcon()
    else FlatTreeClosedIcon()


    val fileIcon: Icon = if (SystemUtils.IS_OS_LINUX) FlatTreeLeafIcon()
    else if (SystemUtils.IS_OS_MAC_OSX)
        UIManager.getIcon("FileView.fileIcon") ?: FlatTreeLeafIcon()
    else if (SystemUtils.IS_OS_WINDOWS) {
        val file = createTempFile()
        val icon = FileSystemView.getFileSystemView().getSystemIcon(file.toFile()) ?: FlatTreeLeafIcon()
        Files.deleteIfExists(file)
        icon
    } else FlatTreeLeafIcon()

}