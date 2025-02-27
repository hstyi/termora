package app.termora.snippet

import app.termora.*
import org.apache.commons.lang3.StringUtils
import java.awt.Dimension
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JScrollPane

class SnippetTreeDialog(owner: Window) : DialogWrapper(owner) {
    private val snippetTree = SnippetTree()
    private val properties get() = Database.getDatabase().properties

    var lastNode: SnippetTreeNode? = null

    init {
        size = Dimension(360, 380)
        title = I18n.getString("termora.snippet.title")
        isModal = true
        isResizable = true
        controlsVisible = false
        setLocationRelativeTo(null)
        init()


        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                properties.putString("SnippetTreeDialog.Tree.expansionState", TreeUtils.saveExpansionState(snippetTree))
                properties.putString("SnippetTreeDialog.Tree.selectionRows", TreeUtils.saveSelectionRows(snippetTree))
            }
        })


        val expansionState = properties.getString("SnippetTreeDialog.Tree.expansionState", StringUtils.EMPTY)
        if (expansionState.isNotBlank()) {
            TreeUtils.loadExpansionState(snippetTree, expansionState)
        }

        val selectionRows = properties.getString("SnippetTreeDialog.Tree.selectionRows", StringUtils.EMPTY)
        if (selectionRows.isNotBlank()) {
            TreeUtils.loadSelectionRows(snippetTree, selectionRows)
        }
    }

    override fun createCenterPanel(): JComponent {
        return JScrollPane(snippetTree).apply { border = BorderFactory.createEmptyBorder(0, 6, 6, 6) }
    }

    override fun doCancelAction() {
        lastNode = null
        super.doCancelAction()
    }

    override fun doOKAction() {
        val node = snippetTree.getLastSelectedPathNode() ?: return
        if (node.isFolder) return
        lastNode = node
        super.doOKAction()
    }

    fun getSelectedNode(): SnippetTreeNode? {
        return lastNode
    }
}