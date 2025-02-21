package app.termora.actions

import app.termora.*
import javax.swing.tree.TreePath

class NewHostAction : AnAction() {
    companion object {

        /**
         * 添加主机对话框
         */
        const val NEW_HOST = "NewHostAction"

    }

    private val hostManager get() = HostManager.getInstance()

    override fun actionPerformed(evt: AnActionEvent) {
        val tree = evt.getData(DataProviders.Welcome.HostTree) ?: return
        var lastNode = (tree.lastSelectedPathComponent ?: tree.model.root) as? HostTreeNode ?: return
        if (lastNode.host.protocol != Protocol.Folder) {
            lastNode = lastNode.parent ?: return
        }

        val lastHost = lastNode.host
        val dialog = HostDialog(evt.window)
        dialog.setLocationRelativeTo(evt.window)
        dialog.isVisible = true
        val host = (dialog.host ?: return).copy(parentId = lastHost.id)

        hostManager.addHost(host)
        val newNode = HostTreeNode(host)

        val model = if (tree.model is FilterableHostTreeModel) (tree.model as FilterableHostTreeModel).getModel()
        else tree.model

        if (model is NewHostTreeModel) {
            model.insertNodeInto(newNode, lastNode, lastNode.childCount)
            tree.selectionPath = TreePath(model.getPathToRoot(newNode))
        }

    }
}