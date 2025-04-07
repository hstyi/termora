package app.termora.terminal.panel.vw

import app.termora.actions.DataProvider
import java.awt.Dimension

interface VisualWindowManager {

    /**
     * 将窗口移动到最前面
     */
    fun moveToFront(visualWindow: VisualWindow)

    /**
     * 添加虚拟窗口
     */
    fun addVisualWindow(visualWindow: VisualWindow)

    /**
     * 移除虚拟窗口
     */
    fun removeVisualWindow(visualWindow: VisualWindow)

    /**
     * 变基，仅仅从 LayeredPane 移除，但是不从 [getVisualWindows] 中移除
     */
    fun rebaseVisualWindow(visualWindow: VisualWindow)

    /**
     * 获取管理的所有窗口
     */
    fun getVisualWindows(): Array<VisualWindow>

    /**
     * 获取管理器的宽高
     */
    fun getDimension(): Dimension

    /**
     * 恢复所有窗口
     */
    fun resumeVisualWindows(id: String, dataProvider: DataProvider)

    /**
     * 存储所有窗口
     */
    fun storeVisualWindows(id: String)
}