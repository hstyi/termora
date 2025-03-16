package app.termora.terminal

import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.reflect.cast
import kotlin.time.measureTime

open class DocumentImpl(private val terminal: Terminal) : Document {
    private val terminalBuffer = TerminalLineBuffer(terminal, false)
    private val screenBuffer = TerminalLineBuffer(terminal, true)
    private val terminalModel = terminal.getTerminalModel()
    private val currentBuffer: TerminalLineBuffer
        get() = if (terminalModel.isAlternateScreenBuffer()) screenBuffer else terminalBuffer

    companion object {
        private val log = LoggerFactory.getLogger(DocumentImpl::class.java)

        /**
         * 超出的行数
         */
        val OverflowLines = DataKey(Int::class)
    }

    init {
        this.terminal.getTerminalModel().addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                if (key != TerminalModel.Resize) {
                    return
                }
                val resize = TerminalModel.Resize.clazz.cast(data)
                this@DocumentImpl.resize(resize.oldSize, resize.newSize)
            }
        })
    }

    override fun getText(): String {
        return currentBuffer.getText()
    }


    override fun eraseInDisplay(n: Int) {
        if (log.isDebugEnabled) {
            val position = terminal.getCursorModel().getPosition()
            log.debug(
                "Erase In Display $n. rows:${terminalModel.getRows()} , y: ${position.y} , x: ${position.x} , attr: ${
                    terminalModel.getData(
                        DataKey.TextStyle
                    )
                }"
            )
        }
        currentBuffer.eraseInDisplay(n)
    }

    override fun eraseInLine(n: Int) {
        currentBuffer.eraseInLine(n)
    }

    override fun getLine(row: Int): TerminalLine {
        return currentBuffer.getLineAt(row - 1)
    }

    override fun getScreenLine(row: Int): TerminalLine {
        return currentBuffer.getScreenLineAt(row - 1)
    }

    override fun write(text: String) {
        currentBuffer.write(text)
        val scrollingModel = terminal.getScrollingModel()
        if (scrollingModel.isStick()) {
            scrollingModel.scrollTo(Int.MAX_VALUE)
        }

    }

    override fun getLineCount(): Int {
        return currentBuffer.getLineCount()
    }

    override fun getCurrentTerminalLineBuffer(): TerminalLineBuffer {
        return currentBuffer
    }

    override fun getScreenTerminalLineBuffer(): TerminalLineBuffer {
        return screenBuffer
    }

    override fun getTerminalLineBuffer(): TerminalLineBuffer {
        return terminalBuffer
    }


    override fun scroll(button: TerminalMouseButton, count: Int) {
        val scrollingRegion = terminal.getTerminalModel().getData(DataKey.ScrollingRegion)
        val top = scrollingRegion.top
        val bottom = scrollingRegion.bottom

        if (button == TerminalMouseButton.ScrollUp) {
            currentBuffer.insertLines(top, bottom, count)
        } else if (button == TerminalMouseButton.ScrollDown) {
            currentBuffer.deleteLines(top, bottom, count)
        }
    }

    override fun deleteLines(offset: Int, count: Int) {
        val scrollingRegion = terminal.getTerminalModel().getData(DataKey.ScrollingRegion)
        currentBuffer.deleteLines(offset, scrollingRegion.bottom, count)
    }


    override fun newline() {
        terminal.getCursorModel().move(CursorMove.Down)
        if (terminal.getTerminalModel().getData(DataKey.AutoNewline, false)) {
            terminal.getCursorModel().move(CursorMove.RowHome)
        }

        val scrollingRegion = terminalModel.getScrollingRegion()
        val caret = terminal.getCursorModel().getPosition()
        if (caret.y > scrollingRegion.bottom) {
            for (i in 0 until abs(caret.y - scrollingRegion.bottom)) {
                scroll(TerminalMouseButton.ScrollDown)
            }
            terminal.getCursorModel().move(row = scrollingRegion.bottom, col = caret.x)
        } else if (caret.y < scrollingRegion.top) {
            terminal.getCursorModel().move(row = scrollingRegion.top, col = caret.x)
        }

        if (terminal.getScrollingModel().isStick()) {
            terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)
        }
    }


    override fun getTerminal(): Terminal {
        return terminal
    }

    private fun resize(oldSize: TerminalSize, newSize: TerminalSize) {
        if (oldSize == newSize) {
            return
        }

        measureTime { terminalBuffer.resize(oldSize, newSize) }.let {
            if (log.isDebugEnabled) {
                log.debug("resize(${getLineCount()}): ${it.inWholeMilliseconds}ms")
            }
        }
    }


}