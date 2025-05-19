package app.termora.terminal

interface SelectionModel {

    companion object {
        /**
         * 变动事件
         */
        val Selection = DataKey(Unit::class)
    }

    /**
     * 获取选中的文字
     *
     * @return 返回空字符串表示没有选中的文本
     */
    fun getSelectedText(): String

    /**
     * 设置选中
     */
    fun setSelection(startPosition: Position, endPosition: Position)

    /**
     * 设置块选中模式
     */
    fun setBlockSelection(block: Boolean)

    /**
     * 是否是块选中模式
     */
    fun isBlockSelection(): Boolean

    /**
     * 获取开始选中的位置
     */
    fun getSelectionStartPosition(): Position

    /**
     * 获取结束的位置
     */
    fun getSelectionEndPosition(): Position

    /**
     * 清除选中
     */
    fun clearSelection()

    /**
     * 获取终端
     */
    fun getTerminal(): Terminal

    /**
     * 判断是否有选中
     */
    fun hasSelection(): Boolean

    /**
     * 判断给定的坐标是否在选中区域内。
     */
    fun hasSelection(position: Position): Boolean

    /**
     * [hasSelection]
     */
    fun hasSelection(x: Int, y: Int): Boolean
}