package app.termora

import app.termora.actions.AnAction
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter
import org.jdesktop.swingx.JXHyperlink
import java.awt.Color
import javax.swing.SwingConstants
import javax.swing.UIManager

class Hyperlink(action: AnAction, focusable: Boolean = true) : JXHyperlink(action) {
    init {
        val myIcon = FlatSVGIcon(Icons.externalLink.name)
        myIcon.colorFilter = object : ColorFilter() {
            override fun filter(color: Color?): Color {
                return UIManager.getColor("Hyperlink.linkColor")
            }
        }
        isFocusable = focusable
        icon = myIcon
        horizontalTextPosition = SwingConstants.LEFT
    }
}