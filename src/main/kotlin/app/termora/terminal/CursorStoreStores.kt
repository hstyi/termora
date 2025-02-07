package app.termora.terminal

import org.slf4j.LoggerFactory


object CursorStoreStores {
    private val log = LoggerFactory.getLogger(CursorStoreStores::class.java)

    fun restore(terminal: Terminal) {
        val terminalModel = terminal.getTerminalModel()
        val cursorStore = if (terminalModel.hasData(DataKey.SaveCursor)) {
            terminalModel.getData(DataKey.SaveCursor)
        } else {
            CursorStore(
                position = Position(1, 1),
                textStyle = TextStyle.Default,
                autoWarpMode = false,
                originMode = false,
                graphicCharacterSet = GraphicCharacterSet()
            )
        }

        terminalModel.setData(DataKey.OriginMode, cursorStore.originMode)
        terminalModel.setData(DataKey.TextStyle, cursorStore.textStyle)
        terminalModel.setData(DataKey.AutoWrapMode, cursorStore.autoWarpMode)
        terminalModel.setData(DataKey.GraphicCharacterSet, cursorStore.graphicCharacterSet)

        val region = if (terminalModel.isOriginMode()) terminalModel.getScrollingRegion()
        else ScrollingRegion(top = 1, bottom = terminalModel.getRows())
        var y = cursorStore.position.y
        if (y < region.top) {
            y = 1
        } else if (y > region.bottom) {
            y = region.bottom
        }

        terminal.getCursorModel().move(row = y, col = cursorStore.position.x)

        if (log.isDebugEnabled) {
            log.debug("Restore Cursor (DECRC). $cursorStore")
        }
    }

    fun store(terminal: Terminal) {
        val terminalModel = terminal.getTerminalModel()

        val graphicCharacterSet = terminalModel.getData(DataKey.GraphicCharacterSet)
        // 避免引用
        val characterSets = mutableMapOf<Graphic, CharacterSet>()
        characterSets.putAll(graphicCharacterSet.characterSets)

        val cursorStore = CursorStore(
            position = terminal.getCursorModel().getPosition(),
            textStyle = terminalModel.getData(DataKey.TextStyle),
            autoWarpMode = terminalModel.getData(DataKey.AutoWrapMode, false),
            originMode = terminalModel.isOriginMode(),
            graphicCharacterSet = graphicCharacterSet.copy(characterSets = characterSets),
        )

        terminalModel.setData(DataKey.SaveCursor, cursorStore)

        if (log.isDebugEnabled) {
            log.debug("Save Cursor (DECSC). $cursorStore")
        }
    }
}