package app.termora

import org.apache.commons.lang3.StringUtils
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

class HostTreeModel : TreeModel {

    val listeners = mutableListOf<TreeModelListener>()

    private val hostManager get() = HostManager.getInstance()
    private val hosts = mutableMapOf<String, Host>()
    private val myRoot by lazy {
        Host(
            id = "0",
            protocol = Protocol.Folder,
            name = I18n.getString("termora.welcome.my-hosts"),
            host = StringUtils.EMPTY,
            port = 0,
            remark = StringUtils.EMPTY,
            username = StringUtils.EMPTY
        )
    }

    init {

        for (host in hostManager.hosts()) {
            hosts[host.id] = host
        }

        hostManager.addHostListener(object : HostListener {
            override fun hostRemoved(id: String) {
                val host = hosts[id] ?: return
                removeNodeFromParent(host)
            }

            override fun hostAdded(host: Host) {
                // 如果已经存在，那么是修改
                if (hosts.containsKey(host.id)) {
                    val oldHost = hosts.getValue(host.id)
                    // 父级结构变了
                    if (oldHost.parentId != host.parentId) {
                        hostRemoved(host.id)
                        hostAdded(host)
                    } else {
                        hosts[host.id] = host
                        val event = TreeModelEvent(this, getPathToRoot(host))
                        listeners.forEach { it.treeStructureChanged(event) }
                    }

                } else {
                    hosts[host.id] = host
                    val parent = getParent(host) ?: return
                    val path = TreePath(getPathToRoot(parent))
                    val event = TreeModelEvent(this, path, intArrayOf(getIndexOfChild(parent, host)), arrayOf(host))
                    listeners.forEach { it.treeNodesInserted(event) }
                }
            }

            override fun hostsChanged() {
                hosts.clear()
                for (host in hostManager.hosts()) {
                    hosts[host.id] = host
                }
                val event = TreeModelEvent(this, getPathToRoot(root), null, null)
                listeners.forEach { it.treeStructureChanged(event) }
            }

        })
    }

    override fun getRoot(): Host {
        return myRoot
    }

    override fun getChild(parent: Any?, index: Int): Any {
        return getChildren(parent)[index]
    }

    override fun getChildCount(parent: Any?): Int {
        return getChildren(parent).size
    }

    override fun isLeaf(node: Any?): Boolean {
        return getChildCount(node) == 0
    }

    fun getParent(node: Host): Host? {
        if (node.parentId == root.id || root.id == node.id) {
            return root
        }
        return hosts.values.firstOrNull { it.id == node.parentId }
    }

    override fun valueForPathChanged(path: TreePath?, newValue: Any?) {

    }

    override fun getIndexOfChild(parent: Any?, child: Any?): Int {
        return getChildren(parent).indexOf(child)
    }

    override fun addTreeModelListener(listener: TreeModelListener) {
        listeners.add(listener)
    }

    override fun removeTreeModelListener(listener: TreeModelListener) {
        listeners.remove(listener)
    }

    /**
     * 仅从结构中删除
     */
    fun removeNodeFromParent(host: Host) {
        val parent = getParent(host) ?: return
        val index = getIndexOfChild(parent, host)
        val event = TreeModelEvent(this, TreePath(getPathToRoot(parent)), intArrayOf(index), arrayOf(host))
        hosts.remove(host.id)
        listeners.forEach { it.treeNodesRemoved(event) }
    }

    fun visit(host: Host, visitor: (host: Host) -> Unit) {
        if (host.protocol == Protocol.Folder) {
            getChildren(host).forEach { visit(it, visitor) }
            visitor.invoke(host)
        } else {
            visitor.invoke(host)
        }
    }

    fun getHost(id: String): Host? {
        return hosts[id]
    }

    fun getPathToRoot(host: Host): Array<Host> {

        if (host.id == root.id) {
            return arrayOf(root)
        }

        val parents = mutableListOf(host)
        var pId = host.parentId
        while (pId != root.id) {
            val e = hosts[(pId)] ?: break
            parents.addFirst(e)
            pId = e.parentId
        }
        parents.addFirst(root)
        return parents.toTypedArray()
    }

    fun getChildren(parent: Any?): List<Host> {
        val pId = if (parent is Host) parent.id else root.id
        return hosts.values.filter { it.parentId == pId }
            .sortedWith(compareBy<Host> { if (it.protocol == Protocol.Folder) 0 else 1 }.thenBy { it.sort })
    }
}