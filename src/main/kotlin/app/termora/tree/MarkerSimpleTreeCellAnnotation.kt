package app.termora.tree

import java.awt.Color
import java.awt.Graphics2D
import javax.swing.JComponent

class MarkerSimpleTreeCellAnnotation(
    val text: String,
    /**
     * 原来的大小 -N
     */
    val fontSize: Float = 0f,
    val foreground: Color? = null, val background: Color? = null,
) : SimpleTreeCellAnnotation {

    override fun getWidth(c: JComponent): Int {
        return stringWidth(c) + 4
    }

    fun stringWidth(c: JComponent): Int {
        return c.getFontMetrics(c.font).stringWidth(text)
    }

    override fun paint(c: JComponent, g: Graphics2D) {
        val width = getWidth(c)


        if (background != null) {
            g.color = background
            g.fillRoundRect(0, 4, width, c.height - 8, 4, 4)
        }

        g.color = foreground ?: c.foreground
        g.font = g.font.deriveFont(g.font.size2D - fontSize)
        val fm = c.getFontMetrics(g.font)

        g.drawString(text, (width - fm.stringWidth(text)) / 2 + 2, (c.height - fm.height) / 2 + fm.ascent)

    }
}