package app.termora

import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

class HostTreeNode(host: Host) : DefaultMutableTreeNode(host) {
    companion object {
        private val hostManager get() = HostManager.getInstance()
    }

    /**
     * 如果要重新赋值，记得修改 [Host.updateDate] 否则下次取出时可能时缓存的
     */
    var host: Host
        get() {
            val cacheHost = hostManager.getHost((userObject as Host).id)
            val myHost = userObject as Host
            if (cacheHost == null) {
                return myHost
            }
            return if (cacheHost.updateDate > myHost.updateDate) cacheHost else myHost
        }
        set(value) = setUserObject(value)

    val folderCount
        get() = children().toList().count { if (it is HostTreeNode) it.host.protocol == Protocol.Folder else false }

    override fun getParent(): HostTreeNode? {
        return super.getParent() as HostTreeNode?
    }

    fun getAllChildren(): List<HostTreeNode> {
        val children = mutableListOf<HostTreeNode>()
        for (child in children()) {
            if (child is HostTreeNode) {
                children.add(child)
                children.addAll(child.getAllChildren())
            }
        }
        return children
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
            if (scopes.isNotEmpty() && !scopes.contains(child.host.protocol)) continue
            val newChildNode = child.clone() as HostTreeNode
            deepClone(newChildNode, child, scopes)
            newNode.add(newChildNode)
        }
    }

    override fun clone(): Any {
        val newNode = HostTreeNode(host)
        newNode.children = null
        newNode.parent = null
        return newNode
    }

    override fun isNodeChild(aNode: TreeNode?): Boolean {
        if (aNode is HostTreeNode) {
            for (node in childrenNode()) {
                if (node.host == aNode.host) {
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

        return host == other.host
    }

    override fun hashCode(): Int {
        return host.hashCode()
    }
}