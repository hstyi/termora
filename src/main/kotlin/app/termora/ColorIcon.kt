package app.termora

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class ColorIcon(
    private val width: Int = 16,
    private val height: Int = 16,
    private val color: Color,
) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        g.color = color
        g.fillRect(x, y, iconWidth, iconHeight)
    }

    override fun getIconWidth(): Int {
        return width
    }

    override fun getIconHeight(): Int {
        return height
    }
}