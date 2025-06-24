package app.termora.plugin.internal.badge

import app.termora.GlassPaneExtension
import app.termora.WindowScope
import app.termora.setupAntialiasing
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.SwingUtilities

class BadgeGlassPaneExtension private constructor() : GlassPaneExtension {
    companion object {
        val instance = BadgeGlassPaneExtension()
    }

    override fun paint(scope: WindowScope, c: JComponent, g2d: Graphics2D) {
        val badges = Badge.getInstance(scope).getBadges()
        if (badges.isEmpty()) return

        setupAntialiasing(g2d)

        for ((comp, presentation) in badges) {
            if (comp.isShowing.not()) continue
            if (presentation.visible.not()) continue
            paintBadge(c, comp, g2d, presentation)
        }

    }

    private fun paintBadge(root: JComponent, c: JComponent, g2d: Graphics2D, presentation: BadgePresentation) {
        val point = c.locationOnScreen
        SwingUtilities.convertPointFromScreen(point, root)
        val size = 6
        g2d.color = presentation.color
        g2d.fillRoundRect(c.width - size - 4 + point.x, point.y + 4, size, size, size, size)
    }


    override fun ordered(): Long {
        return Long.MAX_VALUE
    }
}