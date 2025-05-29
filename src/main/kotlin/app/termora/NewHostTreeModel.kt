package app.termora

import app.termora.account.Account
import app.termora.account.AccountExtension
import app.termora.account.AccountManager
import app.termora.db.DataType
import app.termora.db.DatabaseManager
import app.termora.db.DatabaseManagerExtension
import app.termora.db.OwnerType
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode


class NewHostTreeModel private constructor() : SimpleTreeModel<Host>(
    HostTreeNode(
        Host(
            protocol = "Folder",
            name = "所有主机",
            ownerType = OwnerType.User.name,
        )
    )
), Disposable {

    companion object {
        fun getInstance(): NewHostTreeModel {
            return ApplicationScope.forApplicationScope().getOrCreate(NewHostTreeModel::class) { NewHostTreeModel() }
        }
    }


    private val Host.isRoot get() = this.parentId == "0" || this.parentId.isBlank()
    private val hostManager get() = HostManager.getInstance()
    private val accountManager get() = AccountManager.getInstance()


    init {
        reload()
        registerDynamicExtensions()
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

        val ownerIds = mutableSetOf(accountManager.getAccountId())
        ownerIds.addAll(accountManager.getTeams().map { it.id })
        val hosts = hostManager.hosts().filter { ownerIds.contains(it.ownerId) }
        val nodes = linkedMapOf<String, HostTreeNode>()

        // 如果是根，需要引入团队功能
        if (parent == getRoot()) {
            if (accountManager.hasTeamFeature()) {
                for (team in accountManager.getTeams()) {
                    nodes[team.id] = TeamTreeNode(team)
                }
            }

            nodes[accountManager.getAccountId()] = HostTreeNode(
                Host(
                    id = "0",
                    name = I18n.getString("termora.welcome.my-hosts"),
                    ownerId = accountManager.getAccountId(),
                    ownerType = OwnerType.User.name,
                    protocol = "Folder"
                )
            )
        }

        // 遍历 Host 列表，构建树节点
        for (host in hosts) {
            val node = HostTreeNode(host)
            nodes[host.id] = node
        }

        for (host in hosts) {
            val node = nodes[host.id] ?: continue
            val p = if (host.parentId == "0" || host.parentId.isBlank())
                nodes[accountManager.getAccountId()] else nodes[host.parentId]
            if (p == null) continue
            p.add(node)
        }

        if (parent == getRoot()) {
            if (accountManager.hasTeamFeature()) {
                for (team in accountManager.getTeams()) {
                    parent.add(nodes.getValue(team.id))
                }
            }
            parent.add(nodes.getValue(accountManager.getAccountId()))
        } else {
            for (node in nodes.values) {
                if (node.host.parentId == parent.id) {
                    parent.add(node)
                }
            }
        }

        super.reload(parent)
    }

    override fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int) {
        insertNodeInto(newChild, parent, index, true)
    }

    private fun insertNodeInto(newChild: MutableTreeNode, parent: MutableTreeNode, index: Int, flush: Boolean) {
        super.insertNodeInto(newChild, parent, index)

        if (flush.not()) return

        if (newChild is HostTreeNode) {
            hostManager.addHost(newChild.host)
        }

        // 重置所有排序
        if (parent is HostTreeNode) {
            for ((i, c) in parent.children().toList().filterIsInstance<HostTreeNode>().withIndex()) {
                val sort = i.toLong()
                if (c.host.sort == sort) continue
                c.host = c.host.copy(sort = sort)
                hostManager.addHost(c.host)
            }
        }
    }

    override fun nodeStructureChanged(node: TreeNode?) {
        if (node is HostTreeNode) {
            hostManager.addHost(node.host)
        }
        super.nodeStructureChanged(node)
    }

    override fun removeNodeFromParent(node: MutableTreeNode?) {
        if (node is SimpleTreeNode<*>) {
            for (e in node.getAllChildren()) {
                hostManager.removeHost(e.id)
            }
            hostManager.removeHost(node.id)
        }
        super.removeNodeFromParent(node)
    }

    private fun registerDynamicExtensions() {
        // 底层数据变动刷新
        DynamicExtensionHandler.getInstance()
            .register(DatabaseManagerExtension::class.java, MyDatabaseManagerExtension())
            .let { Disposer.register(this, it) }

        // 用户信息变更
        DynamicExtensionHandler.getInstance()
            .register(AccountExtension::class.java, MyAccountAccountExtension())
            .let { Disposer.register(this, it) }

    }

    private inner class MyDatabaseManagerExtension : DatabaseManagerExtension {
        private val databaseManager get() = DatabaseManager.getInstance()

        override fun onDataChanged(
            id: String,
            type: String,
            action: DatabaseManagerExtension.Action,
            source: DatabaseManagerExtension.Source
        ) {

            if (source != DatabaseManagerExtension.Source.Sync) return
            if (id.isBlank()) return
            if (type.isNotBlank() && type != DataType.Host.name) return
            if (action == DatabaseManagerExtension.Action.Changed) return
            if (action == DatabaseManagerExtension.Action.Added) {
                val host = hostManager.getHost(id) ?: return
                for (node in getRoot().getAllChildren()) {
                    if (node.id == host.parentId) {
                        insertNodeInto(
                            HostTreeNode(host),
                            node,
                            if (host.isFolder) node.folderCount else node.childCount,
                            false
                        )
                        return
                    }
                }
            } else if (action == DatabaseManagerExtension.Action.Removed) {
                for (node in getRoot().getAllChildren()) {
                    if (node.id == id) {
                        removeNodeFromParent(node)
                        return
                    }
                }
            }
        }
    }

    private inner class MyAccountAccountExtension : AccountExtension {
        override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
            if (oldAccount.id != newAccount.id) reload(getRoot())
        }
    }


}