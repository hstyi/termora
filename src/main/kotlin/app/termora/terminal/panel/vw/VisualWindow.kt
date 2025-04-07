package app.termora.terminal.panel.vw

import app.termora.Disposable
import java.awt.Window
import javax.swing.JComponent

/**
 * 虚拟窗口
 */
interface VisualWindow : Disposable {

    /**
     * 虚拟窗口内容
     */
    fun getJComponent(): JComponent

    /**
     * 是否是独立窗口（独立成一个 Window）
     */
    fun isWindow(): Boolean

    /**
     * 如果是独立窗口，那么可以返回
     */
    fun getWindow(): Window? = null

    /**
     * 切换独立模式
     */
    fun toggleWindow()

    /**
     * 同一个类，返回的相同
     */
    fun getWindowName(): String
}