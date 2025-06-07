package app.termora.tree

import app.termora.plugin.ExtensionManager
import app.termora.restore
import app.termora.save
import app.termora.setupAntialiasing
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer

class SimpleTreeCellRenderer : DefaultTreeCellRenderer() {

    private val extensions
        get() = ExtensionManager.getInstance().getExtensions(SimpleTreeCellRendererExtension::class.java)
    private val annotations = mutableListOf<SimpleTreeCellAnnotation>()

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        icon = null
        if (value is SimpleTreeNode<*>) {
            icon = value.getIcon(sel, expanded, hasFocus)
        }

        annotations.clear()
        for (extension in extensions) {
            annotations.addAll(extension.createAnnotations(tree, value, sel, expanded, leaf, row, hasFocus))
        }

        return c
    }

    override fun paint(g: Graphics) {
        super.paint(g)

        if (g is Graphics2D) {
            g.save()
            setupAntialiasing(g)

            var offset = width - annotations.sumOf { it.getWidth(this) + SimpleTreeCellAnnotation.SPACE }

            for (annotation in annotations) {
                g.save()
                g.translate(offset, 0)
                annotation.paint(this, g)
                g.restore()
                offset += annotation.getWidth(this) + SimpleTreeCellAnnotation.SPACE
            }

            g.restore()
        }
    }

    fun getAnnotations(): Array<SimpleTreeCellAnnotation> {
        return annotations.toTypedArray()
    }

}