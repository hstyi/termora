package app.termora

import com.formdev.flatlaf.icons.FlatTreeClosedIcon
import com.formdev.flatlaf.icons.FlatTreeOpenIcon
import javax.swing.Icon
import javax.swing.tree.TreeNode

class HostTreeNode(host: Host) : SimpleTreeNode<Host>(host) {
    companion object {
        private val hostManager get() = HostManager.getInstance()
    }

    var host: Host
        get() = data
        set(value) = setUserObject(value)

    override val isFolder: Boolean
        get() = data.protocol == Protocol.Folder

    override val id: String
        get() = data.id

    /**
     * 如果要重新赋值，记得修改 [Host.updateDate] 否则下次取出时可能时缓存的
     */
    override var data: Host
        get() {
            val cacheHost = hostManager.getHost((userObject as Host).id)
            val myHost = userObject as Host
            if (cacheHost == null) {
                return myHost
            }
            return if (cacheHost.updateDate > myHost.updateDate) cacheHost else myHost
        }
        set(value) = setUserObject(value)

    override val folderCount
        get() = children().toList().count { if (it is HostTreeNode) it.data.protocol == Protocol.Folder else false }

    override fun getParent(): HostTreeNode? {
        return super.getParent() as HostTreeNode?
    }

    override fun getAllChildren(): List<HostTreeNode> {
        return super.getAllChildren().filterIsInstance<HostTreeNode>()
    }

    override fun getIcon(selected: Boolean, expanded: Boolean, hasFocus: Boolean): Icon {
        return when (host.protocol) {
            Protocol.Folder -> if (expanded) FlatTreeOpenIcon() else FlatTreeClosedIcon()
            Protocol.Serial -> if (selected && hasFocus) Icons.plugin.dark else Icons.plugin
            Protocol.RDP -> if (selected && hasFocus) Icons.microsoftWindows.dark else Icons.microsoftWindows
            else -> if (selected && hasFocus) Icons.terminal.dark else Icons.terminal
        }
    }

    fun childrenNode(): List<HostTreeNode> {
        return children?.map { it as HostTreeNode } ?: emptyList()
    }


    /**
     * 深度克隆
     * @param scopes 克隆的范围
     */
    fun clone(scopes: Set<Protocol> = emptySet()): HostTreeNode {
        val newNode = clone() as HostTreeNode
        deepClone(newNode, this, scopes)
        return newNode
    }

    private fun deepClone(newNode: HostTreeNode, oldNode: HostTreeNode, scopes: Set<Protocol> = emptySet()) {
        for (child in oldNode.childrenNode()) {
            if (scopes.isNotEmpty() && !scopes.contains(child.data.protocol)) continue
            val newChildNode = child.clone() as HostTreeNode
            deepClone(newChildNode, child, scopes)
            newNode.add(newChildNode)
        }
    }

    override fun clone(): Any {
        val newNode = HostTreeNode(data)
        newNode.children = null
        newNode.parent = null
        return newNode
    }

    override fun isNodeChild(aNode: TreeNode?): Boolean {
        if (aNode is HostTreeNode) {
            for (node in childrenNode()) {
                if (node.data == aNode.data) {
                    return true
                }
            }
        }
        return super.isNodeChild(aNode)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HostTreeNode

        return data == other.data
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }
}