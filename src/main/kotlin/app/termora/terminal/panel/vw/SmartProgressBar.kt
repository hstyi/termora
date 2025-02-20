package app.termora.terminal.panel.vw

import com.formdev.flatlaf.extras.components.FlatProgressBar
import java.awt.Dimension
import javax.swing.UIManager

class SmartProgressBar : FlatProgressBar() {
    init {
        preferredSize = Dimension(-1, UIManager.getInt("Table.rowHeight") - 6)
        isStringPainted = true
        maximum = 100
        minimum = 0
    }

    override fun setValue(n: Int) {
        super.setValue(n)

        foreground = if (value < 60) {
            UIManager.getColor("Component.accentColor")
        } else if (value < 85) {
            UIManager.getColor("Component.warning.focusedBorderColor")
        } else {
            UIManager.getColor("Component.error.focusedBorderColor")
        }
    }

    override fun updateUI() {
        super.updateUI()
        value = value
    }
}