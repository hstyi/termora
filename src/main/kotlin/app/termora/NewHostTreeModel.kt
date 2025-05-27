package app.termora

import app.termora.Application.ohMyJson
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
            name = I18n.getString("termora.welcome.my-hosts"),
            id = "0",
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
        val root = super.getRoot() as HostTreeNode
        root.host = root.host.copy(ownerId = accountManager.getAccountId())
        return root
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
            if (v.host.isRoot && parent == root) {
                parent.add(v)
            } else if (parent.host.id == v.host.parentId) {
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
                c.host = c.host.copy(sort = sort)
                hostManager.addHost(c.host)
            }
        }
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

        override fun onDataChanged(id: String, type: String, action: DatabaseManagerExtension.Action) {
            if (type.isNotBlank() && type != DataType.Host.name) return
            if (action == DatabaseManagerExtension.Action.Changed) return

            val root = getRoot()
            val children = root.getAllChildren()

            if (action == DatabaseManagerExtension.Action.Added) {
                if (children.any { it.id == id }) return
                val data = databaseManager.data(id) ?: return
                if (data.type != DataType.Host.name) return
                val host = ohMyJson.decodeFromString<Host>(data.data)
                val parent = if (host.parentId == "0" || host.parentId.isBlank()) root else
                    children.firstOrNull { it.id == host.parentId } ?: return

                val node = HostTreeNode(host)
                insertNodeInto(node, parent, if (host.isFolder) parent.folderCount else parent.childCount)

                // 如果是个文件夹，那么强制刷新一下子，因为同步原因，子落库的时候父还没落库
                if (host.isFolder) reload(node)

            } else if (action == DatabaseManagerExtension.Action.Removed) {
                val node = children.firstOrNull { it.id == id } ?: return
                removeNodeFromParent(node)
            }
        }
    }

    private inner class MyAccountAccountExtension : AccountExtension {
        override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
            reload(root)
        }
    }


}