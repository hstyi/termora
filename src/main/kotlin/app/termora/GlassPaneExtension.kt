package app.termora

import app.termora.plugin.Extension
import java.awt.Graphics2D

interface GlassPaneExtension : Extension {
    fun paint(g2d: Graphics2D)
}