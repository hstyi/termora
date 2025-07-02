package app.termora

import com.formdev.flatlaf.ui.FlatSplitPaneUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.function.Supplier
import javax.swing.*

class SplitPaneUI : FlatSplitPaneUI() {
    public override fun startDragging() {
        super.startDragging()
    }

    public override fun dragDividerTo(location: Int) {
        super.dragDividerTo(location)
    }

    public override fun finishDraggingTo(location: Int) {
        super.finishDraggingTo(location)
    }
}

class JSplitPaneWithZeroSizeDivider(
    private val splitPane: JSplitPane,
    private val topOffset: Supplier<Int>,
) : JPanel(BorderLayout()) {

    private val dividerDragSize = 7
    private val layeredPane = LayeredPane()
    private val divider = Divider()

    init {
        layeredPane.add(splitPane, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(divider, JLayeredPane.PALETTE_LAYER as Any)
        add(layeredPane, BorderLayout.CENTER)
    }

    private inner class Divider : JComponent() {
        private var dragging = false
        private var dragStartX = 0
        private var initialDividerLocation = 0
        private val splitPaneUI get() = splitPane.ui as SplitPaneUI

        init {
            cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        dragging = true
                        dragStartX = e.xOnScreen
                        initialDividerLocation = splitPane.dividerLocation
                        splitPaneUI.startDragging()
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (dragging) {
                            val deltaX = e.xOnScreen - dragStartX
                            val newLocation = initialDividerLocation + deltaX
                            splitPaneUI.finishDraggingTo(newLocation)
                        }
                        dragging = false
                    }
                }
            })

            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    if (dragging) {
                        val deltaX = e.xOnScreen - dragStartX
                        val newLocation = initialDividerLocation + deltaX
                        splitPaneUI.dragDividerTo(newLocation)
                    }
                }
            })
        }

        override fun paint(g: Graphics) {
            g.color = UIManager.getColor("controlShadow")
            g.fillRect(width / 2, 0, 1, height)
        }
    }


    private inner class LayeredPane : JLayeredPane() {
        private val w get() = (dividerDragSize - 1) / 2

        override fun doLayout() {
            synchronized(treeLock) {
                for (c in components) {
                    if (c == divider) {
                        c.setBounds(
                            splitPane.dividerLocation - w,
                            topOffset.get(),
                            dividerDragSize,
                            height - topOffset.get()
                        )
                    } else {
                        c.setBounds(0, 0, width, height)
                    }
                }
            }
        }

        override fun paint(g: Graphics) {
            super.paint(g)
            g.color = UIManager.getColor("controlShadow")
            g.fillRect(splitPane.dividerLocation, 0, 1, topOffset.get())
        }
    }

    override fun doLayout() {
        super.doLayout()
        layeredPane.doLayout()
    }


}