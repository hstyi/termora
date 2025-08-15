package app.termora.tree

import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

abstract class SimpleTreeNode<T>(data: T) : DefaultMutableTreeNode(data) {
    @Suppress("UNCHECKED_CAST")
    open var data: T
        get() = userObject as T
        set(value) = setUserObject(value)

    @Suppress("UNCHECKED_CAST")
    override fun getParent(): SimpleTreeNode<T>? {
        return super.getParent() as SimpleTreeNode<T>?
    }

    open val folderCount: Int get() = 0

    open fun getIcon(selected: Boolean, expanded: Boolean, hasFocus: Boolean): Icon? {
        return null
    }

    open val isFolder get() = false

    abstract val id: String

    @Suppress("UNCHECKED_CAST")
    open fun getAllChildren(): List<SimpleTreeNode<T>> {
        val children = mutableListOf<SimpleTreeNode<T>>()
        for (child in children()) {
            val c = child as? SimpleTreeNode<T> ?: continue
            children.add(c)
            children.addAll(c.getAllChildren())
        }
        return children
    }

    open fun getParents(): List<SimpleTreeNode<T>> {
        val parents = mutableListOf<SimpleTreeNode<T>>()
        var p = parent as TreeNode?
        while (p != null) {
            if (p is SimpleTreeNode<T>) {
                parents.add(p)
            }
            p = p.parent
        }
        return parents
    }
}