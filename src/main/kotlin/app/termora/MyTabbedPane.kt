package app.termora

import com.formdev.flatlaf.extras.components.FlatTabbedPane

class MyTabbedPane : FlatTabbedPane() {
    override fun setSelectedIndex(index: Int) {
        val oldIndex = selectedIndex
        super.setSelectedIndex(index)
        firePropertyChange("selectedIndex", oldIndex, index)
    }

}