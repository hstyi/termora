package app.termora

import java.awt.Graphics2D
import javax.swing.JComponent

class BadgeGlassPaneExtension private constructor() : GlassPaneExtension {
    companion object {
        val instance = BadgeGlassPaneExtension()
    }

    override fun paint(c: JComponent, g2d: Graphics2D): Boolean {
        return false
    }

    override fun ordered(): Long {
        return 0
    }
}