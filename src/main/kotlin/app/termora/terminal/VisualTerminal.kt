package app.termora.terminal

import org.slf4j.LoggerFactory
import kotlin.math.max

open class VisualTerminal : Terminal {

    private val terminalModel by lazy { TerminalModelImpl(this) }
    private val document by lazy { DocumentImpl(this) }
    private val selectionModel by lazy { SelectionModelImpl(this) }
    private val cursorModel by lazy { CursorModelImpl(this) }
    private val scrollingModel by lazy { ScrollingModelImpl(this) }
    private val markupModel by lazy { MarkupModelImpl(this) }
    private val keyEncoder by lazy { KeyEncoderImpl(this) }
    private val findModel by lazy { FindModelImpl(this) }
    private val tabulator by lazy { TabulatorImpl(this) }

    private var listeners = listOf<TerminalListener>()

    /**
     * 读取器
     */
    private val reader = TerminalReader()

    /**
     * 处理器
     */
    @get:JvmName("get_processor")
    private val processor by lazy { MyProcessor(this, reader) }


    companion object {

        private val log = LoggerFactory.getLogger(VisualTerminal::class.java)
        val Written = DataKey(String::class)

    }

    override fun write(text: String) {
        reader.addLast(text)
        while (reader.isNotEmpty()) {
            try {
                val c = reader.read()
                getProcessor().process(c)
            } catch (e: Exception) {
                if (log.isWarnEnabled) {
                    log.warn(e.message ?: e.toString(), e)
                }
                continue
            }
        }
        getTerminalModel().setData(Written, text)
    }

    private fun getProcessor(): MyProcessor {
        return processor
    }


    override fun getTabulator(): Tabulator {
        return tabulator
    }

    override fun getDocument(): Document {
        return document
    }

    override fun getFindModel(): FindModel {
        return findModel
    }


    override fun getTerminalModel(): TerminalModel {
        return terminalModel
    }

    override fun getSelectionModel(): SelectionModel {
        return selectionModel
    }

    override fun getCursorModel(): CursorModel {
        return cursorModel
    }

    override fun getMarkupModel(): MarkupModel {
        return markupModel
    }

    override fun getScrollingModel(): ScrollingModel {
        return scrollingModel
    }


    override fun getKeyEncoder(): KeyEncoder {
        return keyEncoder
    }

    override fun close() {
        getTerminalListeners().forEach { it.onClose(this) }
    }


    override fun addTerminalListener(listener: TerminalListener) {
        listeners = listeners.toMutableList()
            .apply { add(listener) }.toList()
    }

    override fun getTerminalListeners(): List<TerminalListener> {
        return listeners
    }

    override fun removeTerminalListener(listener: TerminalListener) {
        listeners = listeners.toMutableList()
            .apply { remove(listener) }.toList()
    }


}

private class MyProcessor(private val terminal: Terminal, reader: TerminalReader) {
    private var state: ProcessorState = TerminalState.READY
    private val document get() = terminal.getDocument()
    private val cursorModel get() = terminal.getCursorModel()
    private val terminalModel get() = terminal.getTerminalModel()

    companion object {
        private val log = LoggerFactory.getLogger(MyProcessor::class.java)
    }

    val processors = mutableMapOf<ProcessorState, Processor>(
        TerminalState.EscapeSequence to EscapeSequenceProcessor(terminal, reader),
        TerminalState.CSI to ControlSequenceIntroducerProcessor(terminal, reader),
        TerminalState.OSC to OperatingSystemCommandProcessor(terminal, reader),
        TerminalState.ESC_LPAREN to EscapeDesignateCharacterSetProcessor(terminal, reader),
        TerminalState.DCS to DeviceControlStringProcessor(terminal, reader),
        TerminalState.Text to TextProcessor(terminal, reader),
    )

    fun process(ch: Char) {
        if (log.isTraceEnabled) {
            val position = cursorModel.getPosition()
            log.trace("process [${printChar(ch)}]  , state: $state , position: $position")
        }


        val processor = processors[state]
        if (processor != null) {
            state = processor.process(ch)
            return
        }


        state = when (ch) {
            ControlCharacters.ESC -> TerminalState.EscapeSequence
            ControlCharacters.BEL -> {
                terminal.getTerminalModel().bell()
                TerminalState.READY
            }

            ControlCharacters.CR -> {
                cursorModel.move(CursorMove.RowHome)
                TerminalState.READY
            }

            ControlCharacters.TAB -> {
                val position = cursorModel.getPosition()
                // Next tab + 1，如果当前 x = 11，那么下一个就是 16，因为在 TerminalLineBuffer#writeTerminalLineChar 的时候会 - 1 会导致错乱一位
                val nextTab = terminal.getTabulator().nextTab(position.x - 1) + 1
                val length = if (terminalModel.isAlternateScreenBuffer()) {
                    document.getCurrentTerminalLineBuffer()
                        .getLineAt(position.y - 1).getText().length
                } else {
                    document.getCurrentTerminalLineBuffer()
                        .getScreenLineAt(position.y - 1)
                        .getText().length
                }

                val x = max(position.x - 1, length)
                if (x < nextTab) {
                    cursorModel.move(row = position.y, col = (position.x - 1) + (nextTab - x))
                } else {
                    cursorModel.move(row = position.y, col = nextTab)
                }
                TerminalState.READY
            }

            ControlCharacters.LF,
            ControlCharacters.VT,
            ControlCharacters.FF -> {
                terminal.getDocument().newline()
                TerminalState.READY
            }

            ControlCharacters.BS -> {
                cursorModel.move(CursorMove.Left)
                TerminalState.READY
            }

            ControlCharacters.SI -> {
                terminalModel.getData(DataKey.GraphicCharacterSet).use(Graphic.G0)
                if (log.isDebugEnabled) {
                    log.debug("Use Graphic.G0")
                }
                TerminalState.READY
            }

            ControlCharacters.SO -> {
                terminalModel.getData(DataKey.GraphicCharacterSet).use(Graphic.G1)
                if (log.isDebugEnabled) {
                    log.debug("Use Graphic.G1")
                }
                TerminalState.READY
            }

            else -> processors.getValue(TerminalState.Text).process(ch)
        }

//        log.info("{}", terminal.getDocument().getText())


    }


}


private fun printChar(ch: Char): String {
    when (ch) {
        ControlCharacters.ESC -> {
            return "ESC"
        }

        ControlCharacters.BEL -> {
            return "BEL"
        }

        '\u0000' -> {
            return "\\u0000"
        }

        ControlCharacters.CR -> {
            return "\\r"
        }

        ControlCharacters.LF -> {
            return "\\n"
        }

        ControlCharacters.BS -> {
            return "\\b"
        }

        ControlCharacters.SP -> {
            return "SPACE"
        }

        '\uFE0F' -> {
            return "\\uFE0F"
        }

        ControlCharacters.TAB -> {
            return "\\t"
        }

        else -> return ch.toString()
    }
}


internal open class InternalEvent(open val event: TerminalEvent) : TerminalEvent(0)
internal class InternalMouseClickedEvent(val position: Position, override val event: TerminalMouseEvent) :
    InternalEvent(event)


