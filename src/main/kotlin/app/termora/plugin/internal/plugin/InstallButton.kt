package app.termora.plugin.internal.plugin

import app.termora.DynamicColor
import app.termora.setupAntialiasing
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.ui.FlatButtonUI
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.math.round

open class InstallButton : FlatButton() {
    var progress: Int = 0
    var installing = false
        set(value) {
            field = value
            repaint()
        }
    var update = false

    private var paintingBackground = false


    init {
        setUI(InstallButtonUI())
    }

    override fun updateUI() {
        setUI(InstallButtonUI())
        super.updateUI()
    }


    override fun getWidth(): Int {
        val width = super.getWidth()
        if (installing && paintingBackground) {
            return round(width * (progress / 100.0)).toInt()
        }
        return width
    }

    override fun getText(): String? {
        if (installing) {
            return "${progress}%"
        }
        return super.getText()
    }

    override fun paint(g: Graphics?) {
        super.paint(g)
        if (g is Graphics2D) {
            setupAntialiasing(g)
            if (update && installing.not()) {
                val size = 6
                g.color = UIManager.getColor("Component.error.focusedBorderColor")
                g.fillRoundRect(width - size - 4, 4, size, size, size, size)
            }
        }
    }


    private inner class InstallButtonUI : FlatButtonUI(true) {
        override fun paintBackground(g: Graphics?, c: JComponent?) {
            if (installing) {
                paintingBackground = true
                super.paintBackground(g, c)
                paintingBackground = false
            } else {
                super.paintBackground(g, c)
            }
        }

        override fun getBackground(c: JComponent): Color? {
            if (installing && paintingBackground && c.width > 0) {
                return DynamicColor.BorderColor
            }
            return super.getBackground(c)
        }
    }
}