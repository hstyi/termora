package app.termora.terminal.panel.vw

import com.formdev.flatlaf.ui.FlatWindowResizer
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent

class VisualWindowResizer(resizeComp: JComponent, private val windowResizable: () -> Boolean = { true }) :
    FlatWindowResizer(resizeComp) {

    override fun isWindowResizable(): Boolean {
        return windowResizable.invoke()
    }

    override fun getWindowBounds(): Rectangle {
        return resizeComp.bounds
    }

    override fun setWindowBounds(r: Rectangle) {
        resizeComp.bounds = r
        resizeComp.revalidate()
        resizeComp.repaint()
    }

    override fun limitToParentBounds(): Boolean {
        return true
    }

    override fun getParentBounds(): Rectangle {
        return resizeComp.getParent().bounds
    }

    override fun honorMinimumSizeOnResize(): Boolean {
        return true
    }

    override fun honorMaximumSizeOnResize(): Boolean {
        return true
    }

    override fun getWindowMinimumSize(): Dimension {
        return resizeComp.minimumSize
    }

    override fun getWindowMaximumSize(): Dimension {
        return resizeComp.maximumSize
    }
}