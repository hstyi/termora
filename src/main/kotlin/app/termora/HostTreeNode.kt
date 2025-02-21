package app.termora

import javax.swing.tree.DefaultMutableTreeNode

class HostTreeNode(host: Host) : DefaultMutableTreeNode(host) {
    companion object {
        private val hostManager get() = HostManager.getInstance()
    }

    var host: Host
        get() = hostManager.getHost((userObject as Host).id) ?: userObject as Host
        set(value) {
            setUserObject(value)
            hostManager.setHost(value)
        }

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


    override fun clone(): Any {
        val newNode = HostTreeNode(host)
        newNode.children = null
        newNode.parent = null
        return newNode
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