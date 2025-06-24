package app.termora.plugin.internal.badge

import app.termora.Disposable
import java.awt.Color

interface BadgePresentation : Disposable {

    /**
     * 是否显示
     */
    var visible: Boolean

    /**
     * 颜色
     */
    var color: Color

}