package app.termora

import org.apache.commons.lang3.StringUtils
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode


class NewHostTreeModel : SimpleTreeModel<Host>(
    HostTreeNode(
        Host(
            id = "0",
            protocol = Protocol.Folder,
            name = I18n.getString("termora.welcome.my-hosts"),
            host = StringUtils.EMPTY,
            port = 0,
            remark = StringUtils.EMPTY,
            username = StringUtils.EMPTY
        )
    )
) {
    private val Host.isRoot get() = this.parentId == "0" || this.parentId.isBlank()
    private val hostManager get() = HostManager.getInstance()

    init {
        reload()
    }


    override fun getRoot(): HostTreeNode {
        return super.getRoot() as HostTreeNode
    }


    override fun reload(parent: TreeNode) {

        if (parent !is HostTreeNode) {
            super.reload(parent)
            return
        }

        parent.removeAllChildren()

        val hosts = hostManager.hosts()
        val nodes = linkedMapOf<String, HostTreeNode>()

        // 遍历 Host 列表，构建树节点
        for (host in hosts) {
            val node = HostTreeNode(host)
            nodes[host.id] = node
        }

        for (host in hosts) {
            val node = nodes[host.id] ?: continue
            if (host.isRoot) continue
            val p = nodes[host.parentId] ?: continue
            p.add(node)
        }

        for ((_, v) in nodes.entries) {
            if (parent.host.id == v.host.parentId) {
                parent.add(v)
            }
        }

        super.reload(parent)
    }

    override fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int) {
        super.insertNodeInto(newChild, parent, index)
        // 重置所有排序
        if (parent is HostTreeNode) {
            for ((i, c) in parent.children().toList().filterIsInstance<HostTreeNode>().withIndex()) {
                val sort = i.toLong()
                if (c.host.sort == sort) continue
                c.host = c.host.copy(sort = sort, updateDate = System.currentTimeMillis())
                hostManager.addHost(c.host)
            }
        }
    }


}