package app.termora.transfer

import app.termora.plugin.Extension
import java.awt.Window
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.JMenuItem

internal interface TransportContextMenuExtension : Extension {

    /**
     * 抛出 [UnsupportedOperationException] 表示不支持
     *
     * @param fileSystem 为 null 表示可能已经断线，处于不可用状态
     */
    fun createJMenuItem(
        window: Window,
        fileSystem: FileSystem?,
        popupMenu: TransportPopupMenu,
        files: List<Pair<Path, TransportTableModel.Attributes>>
    ): JMenuItem
}