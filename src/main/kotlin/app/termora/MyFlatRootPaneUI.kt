package app.termora

import com.formdev.flatlaf.ui.FlatRootPaneUI
import com.formdev.flatlaf.ui.FlatTitlePane

class MyFlatRootPaneUI : FlatRootPaneUI() {

    fun getTitlePane(): FlatTitlePane? {
        return super.titlePane
    }
}