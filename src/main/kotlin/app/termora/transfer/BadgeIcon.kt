package app.termora.transfer

import app.termora.restore
import app.termora.save
import app.termora.setupAntialiasing
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.UIManager


class BadgeIcon(
    private val icon: Icon,
    var visible: Boolean = false
) : Icon {

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        icon.paintIcon(c, g, x, y)
        if (g is Graphics2D) {
            if (visible) {
                g.save()
                setupAntialiasing(g)
                val size = 6
                g.color = UIManager.getColor("Component.error.focusedBorderColor")
                g.fillRoundRect(c.width - size - 4, 4, size, size, size, size)
                g.restore()
            }
        }
    }

    override fun getIconWidth(): Int {
        return icon.iconWidth
    }

    override fun getIconHeight(): Int {
        return icon.iconHeight
    }
}