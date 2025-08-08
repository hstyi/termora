package app.termora.plugins.bg

import app.termora.GlassPaneExtension
import app.termora.WindowScope
import app.termora.restore
import app.termora.save
import com.formdev.flatlaf.FlatLaf
import java.awt.AlphaComposite
import java.awt.Graphics2D
import javax.swing.JComponent

class BGGlassPaneExtension private constructor() : GlassPaneExtension {
    companion object {
        val instance = BGGlassPaneExtension()
    }


    override fun paint(scope: WindowScope, c: JComponent, g2d: Graphics2D) {

        val img = BackgroundManager.getInstance().getBackgroundImage() ?: return
        g2d.save()
        g2d.composite = AlphaComposite.getInstance(
            AlphaComposite.SRC_OVER,
            if (FlatLaf.isLafDark()) 0.2f else 0.1f
        )
        
        when (Appearance.fillMode) {
            FillMode.STRETCH.name -> {
                g2d.drawImage(img, 0, 0, c.width, c.height, null)
            }

            FillMode.CENTER.name -> {
                val x = (c.width - img.width) / 2
                val y = (c.height - img.height) / 2
                g2d.drawImage(img, x, y, null)
            }

            FillMode.TILE.name -> {
                val iw = img.width
                val ih = img.height
                var y = 0
                while (y < c.height) {
                    var x = 0
                    while (x < c.width) {
                        g2d.drawImage(img, x, y, null)
                        x += iw
                    }
                    y += ih
                }
            }

            FillMode.FIT.name -> {
                val scale = maxOf(c.width.toDouble() / img.width, c.height.toDouble() / img.height)
                val newW = (img.width * scale).toInt()
                val newH = (img.height * scale).toInt()
                val x = (c.width - newW) / 2
                val y = (c.height - newH) / 2
                g2d.drawImage(img, x, y, newW, newH, null)
            }
        }

        g2d.restore()

    }
}