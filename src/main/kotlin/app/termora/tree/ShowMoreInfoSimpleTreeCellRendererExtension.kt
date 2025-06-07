package app.termora.tree

import app.termora.ApplicationScope
import app.termora.Disposable
import app.termora.Disposer
import app.termora.DynamicColor
import app.termora.database.DatabaseManager
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.plugin.internal.rdp.RDPProtocolProvider
import app.termora.plugin.internal.serial.SerialProtocolProvider
import app.termora.plugin.internal.ssh.SSHProtocolProvider
import org.apache.commons.lang3.StringUtils
import javax.swing.JTree

/**
 * 显示更多信息的扩展
 */
class ShowMoreInfoSimpleTreeCellRendererExtension private constructor() : SimpleTreeCellRendererExtension, Disposable {

    private val properties get() = DatabaseManager.getInstance().properties
    private val isShowMoreInfo
        get() = properties.getString("HostTree.showMoreInfo", "false").toBoolean()

    companion object {
        fun getInstance(): ShowMoreInfoSimpleTreeCellRendererExtension {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(ShowMoreInfoSimpleTreeCellRendererExtension::class) { ShowMoreInfoSimpleTreeCellRendererExtension() }
        }
    }

    init {
        DynamicExtensionHandler.getInstance()
            .register(SimpleTreeCellRendererExtension::class.java, this)
            .let { Disposer.register(this, it) }
    }

    override fun createAnnotations(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): List<SimpleTreeCellAnnotation> {
        if (isShowMoreInfo.not()) return emptyList()
        val node = value as? SimpleTreeNode<*> ?: return emptyList()

        val foreground = DynamicColor("textInactiveText")
        var text = StringUtils.EMPTY

        if (node.isFolder) {
            text = "(${node.getAllChildren().size})"
        } else if (node is HostTreeNode) {
            val host = node.host
            if (host.protocol == SSHProtocolProvider.PROTOCOL || host.protocol == RDPProtocolProvider.PROTOCOL) {
                text = "${host.username}@${host.host}"
            } else if (host.protocol == SerialProtocolProvider.PROTOCOL) {
                text = host.options.serialComm.port
            }
        }

        return listOf(MarkerSimpleTreeCellAnnotation(text, foreground = if (sel) null else foreground))
    }

    /**
     * 优先级最高
     */
    override fun ordered(): Long {
        return Long.MIN_VALUE
    }
}