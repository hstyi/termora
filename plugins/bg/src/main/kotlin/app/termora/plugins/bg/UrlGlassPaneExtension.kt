package app.termora.plugins.bg

import app.termora.GlassPaneExtension
import java.awt.Graphics2D

class UrlGlassPaneExtension private constructor() : GlassPaneExtension {
    companion object {
        val instance by lazy { UrlGlassPaneExtension() }
    }

    override fun paint(backageImage: String, g2d: Graphics2D): Boolean {
        return false
    }
}