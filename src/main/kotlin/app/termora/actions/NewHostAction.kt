package app.termora.actions

import app.termora.Host
import app.termora.HostDialog
import app.termora.HostManager
import app.termora.Protocol
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
        val model = tree.model
        var lastHost = tree.lastSelectedPathComponent ?: model.root
        if (lastHost !is Host) {
            return
        }

        if (lastHost.protocol != Protocol.Folder) {
            val p = model.getParent(lastHost) ?: return
            lastHost = p
        }

        val dialog = HostDialog(evt.window)
        dialog.setLocationRelativeTo(evt.window)
        dialog.isVisible = true
        val host = (dialog.host ?: return).copy(parentId = lastHost.id)

        hostManager.addHost(host)

        tree.expandNode(lastHost)

        tree.selectionPath = TreePath(model.getPathToRoot(host))

    }
}