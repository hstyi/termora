package app.termora.terminal

/**
 * https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#h4-Controls-beginning-with-ESC:ESC-7.C65
 */
data class CursorStore(
    /**
     * @see [CursorModel.getPosition]
     */
    val position: Position,
    /**
     * @see [DataKey.TextStyle]
     */
    val textStyle: TextStyle,
    /**
     * 如果为 null 表示没有设置
     *
     * @see [DataKey.AutoWrapMode]
     */
    val autoWarpMode: Boolean?,
    /**
     * @see [DataKey.OriginMode]
     */
    val originMode: Boolean,
    /**
     * 图形字符集
     */
    val graphicCharacterSet: GraphicCharacterSet
)