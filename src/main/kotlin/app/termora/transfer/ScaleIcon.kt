package app.termora.transfer

import app.termora.restore
import app.termora.save
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

class ScaleIcon(private val icon: Icon, private val size: Int) : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        if (g is Graphics2D) {
            g.save()
            val iconWidth = icon.iconWidth.toDouble()
            val iconHeight = icon.iconHeight.toDouble()
            g.translate(x, y)
            g.scale(getIconWidth() / iconWidth, getIconHeight() / iconHeight)
            icon.paintIcon(c, g, 0, 0)
            g.restore()
        }
    }

    override fun getIconWidth(): Int {
        return size
    }

    override fun getIconHeight(): Int {
        return size
    }
}