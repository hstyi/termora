package app.termora

import app.termora.plugin.Extension
import java.awt.Graphics2D
import javax.swing.JComponent

/**
 * 玻璃面板扩展
 */
interface GlassPaneExtension : Extension {


    fun paint(scope: WindowScope, c: JComponent, g2d: Graphics2D)

}