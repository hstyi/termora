package app.termora.highlight

import java.awt.Color
import javax.swing.JPanel

class ColorPanel : JPanel() {
    var color: Color? = null
        set(value) {
            background = value
            val old = field
            field = value
            firePropertyChange("color", old, value)
        }
    var colorIndex = -1

}