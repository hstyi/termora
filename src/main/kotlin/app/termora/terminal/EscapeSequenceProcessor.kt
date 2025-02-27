package app.termora.terminal

import org.slf4j.LoggerFactory

class EscapeSequenceProcessor(terminal: Terminal, reader: TerminalReader) : AbstractProcessor(terminal, reader) {

    private val terminalModel get() = terminal.getTerminalModel()
    private val vt52Mode get() = terminalModel.getData(DataKey.VT52Mode, false)

    companion object {
        private val log = LoggerFactory.getLogger(EscapeSequenceProcessor::class.java)
    }

    override fun process(ch: Char): ProcessorState {
        var state = TerminalState.READY
        when (ch) {
            '[' -> {
                state = TerminalState.CSI
            }

            ']' -> {
                state = TerminalState.OSC
            }

            // Exit VT52 mode (Enter VT100 mode).
            '<' -> {
                terminalModel.setData(DataKey.VT52Mode, false)
                if (log.isDebugEnabled) {
                    log.debug("Exit VT52 mode (Enter VT100 mode).")
                }
            }

            // Normal Keypad (DECKPNM)
            '>' -> {
                terminal.getTerminalModel().setData(DataKey.AlternateKeypad, false)
                if (log.isDebugEnabled) {
                    log.debug("Normal Keypad (DECKPNM) false")
                }
            }

            // Application Keypad (DECKPAM).
            '=' -> {
                terminal.getTerminalModel().setData(DataKey.AlternateKeypad, true)
                if (log.isDebugEnabled) {
                    log.debug("Application Keypad (DECKPAM) true")
                }
            }

            // ESC A     Cursor up.
            'A' -> {
                if (vt52Mode) {
                    reader.addFirst(listOf(ControlCharacters.ESC, '[', '1', 'A'))
                    if (log.isDebugEnabled) {
                        log.debug("ESC A     Cursor up.")
                    }
                } else {
                    if (log.isWarnEnabled) {
                        log.warn("ESC A     Cursor up. (Not VT52 Mode)")
                    }
                }
            }

            // ESC B     Cursor down.
            'B' -> {
                if (vt52Mode) {
                    reader.addFirst(listOf(ControlCharacters.ESC, '[', '1', 'B'))
                    if (log.isDebugEnabled) {
                        log.debug("ESC B     Cursor down.")
                    }
                } else {
                    if (log.isWarnEnabled) {
                        log.warn("ESC B     Cursor down. (Not VT52 Mode)")
                    }
                }

            }

            // ESC C     Cursor right.
            'C' -> {
                if (vt52Mode) {
                    reader.addFirst(listOf(ControlCharacters.ESC, '[', '1', 'C'))
                    if (log.isDebugEnabled) {
                        log.debug("ESC C     Cursor right.")
                    }
                } else {
                    if (log.isWarnEnabled) {
                        log.warn("ESC C     Cursor right. (Not VT52 Mode)")
                    }
                }

            }

            // Reverse Index (RI  is 0x8d).
            'M' -> {
                val position = terminal.getCursorModel().getPosition()
                val top = terminalModel.getScrollingRegion().top
                // 如果等于顶边，页面向上滚动光标不变
                if (position.y == top) {
                    terminal.getDocument().scroll(TerminalMouseButton.ScrollUp)
                } else {
                    terminal.getCursorModel().move(CursorMove.Up)
                }

                if (log.isDebugEnabled) {
                    log.debug("Reverse Index (RI  is 0x8d).")
                }


            }

            // Single Shift Select of G2 Character Set (SS2  is 0x8e), VT220.
            // This affects next character only.
            'N' -> {
                terminalModel.getData(DataKey.GraphicCharacterSet).useOnce(Graphic.G2)
                if (log.isDebugEnabled) {
                    log.debug("Single Shift Select of G2 Character Set (SS2  is 0x8e), VT220.")
                }

            }

            // Single Shift Select of G3 Character Set (SS3  is 0x8f), VT220.
            // This affects next character only.
            'O' -> {
                terminalModel.getData(DataKey.GraphicCharacterSet).useOnce(Graphic.G3)
                if (log.isDebugEnabled) {
                    log.debug("Single Shift Select of G3 Character Set (SS3  is 0x8f), VT220.")
                }

            }

            // Device Control String (DCS  is 0x90).
            'P' -> {
                state = TerminalState.DCS
            }

            // Start of Guarded Area (SPA  is 0x96).
            'V' -> {
                if (log.isWarnEnabled) {
                    log.warn("Start of Guarded Area (SPA  is 0x96).")
                }

            }

            // End of Guarded Area (EPA  is 0x97).
            'W' -> {
                if (log.isWarnEnabled) {
                    log.warn("End of Guarded Area (EPA  is 0x97).")
                }

            }

            // ESC I     Reverse line feed
            'I' -> {
                if (vt52Mode) {
                    if (log.isDebugEnabled) {
                        log.debug("Reverse line feed.")
                    }
                    terminal.getCursorModel().move(CursorMove.Up)
                }

            }

            // ESC H Tab Set (HTS  is 0x88).
            // VT52 Mode: ESC H     Move the cursor to the home position.
            'H' -> {
                if (vt52Mode) {
                    // Move the cursor to the home position.
                    reader.addFirst(listOf(ControlCharacters.ESC, '[', '1', ';', '1', 'H'))
                    if (log.isDebugEnabled) {
                        log.debug("Move the cursor to the home position.")
                    }
                } else {
                    val x = terminal.getCursorModel().getPosition().x
                    terminal.getTabulator().setTabStop(x - 1)
                    if (log.isDebugEnabled) {
                        log.debug("Horizontal Tab Set (HTS). col: $x")
                    }
                }

            }

            // VT52 Mode: ESC K     Erase from the cursor to the end of the line.
            'K' -> {
                if (vt52Mode) {
                    // 清除光标行到末尾
                    terminal.getDocument().eraseInLine(0)
                    if (log.isDebugEnabled) {
                        log.debug("Erase from the cursor to the end of the screen.")
                    }
                } else {
                    if (log.isWarnEnabled) {
                        log.warn("ESC K     Erase from the cursor to the end of the line. (Not VT52 Mode)")
                    }
                }

            }

            // Designate G0 Character Set, VT100, ISO 2022.
            '*',
            '+',
            ')',
            '-',
            '.',
            '/',
            '(' -> {
                // 设置要处理的图形处理器，在后续会用到
                terminalModel.setData(
                    EscapeDesignateCharacterSetProcessor.Graphic, when (ch) {
                        '*' -> Graphic.G2
                        '+' -> Graphic.G3
                        ')' -> Graphic.G1
                        '-' -> Graphic.G1
                        '.' -> Graphic.G2
                        '/' -> Graphic.G3
                        '(' -> Graphic.G0
                        else -> Graphic.G0
                    }
                )
                state = TerminalState.ESC_LPAREN
            }

            // ESC D Index (IND  is 0x84).
            // VT52 Mode: Cursor left.
            'D' -> {
                if (vt52Mode) {
                    reader.addFirst(listOf(ControlCharacters.ESC, '[', '1', 'D'))
                    if (log.isDebugEnabled) {
                        log.debug("Index (IND  is 0x84).")
                    }
                } else {
                    val oldPosition = terminal.getCursorModel().getPosition()
                    // 超出底部滚动
                    if (oldPosition.y == terminalModel.getScrollingRegion().bottom) {
                        terminal.getDocument().newline()
                    } else {
                        terminal.getCursorModel().move(CursorMove.Down)
                    }

                    val newPosition = terminal.getCursorModel().getPosition()

                    if (log.isDebugEnabled) {
                        log.debug("Index (IND  is 0x84). old: $oldPosition , new: $newPosition")
                    }
                }

            }


            // VT52 Mode: ESC J     Erase from the cursor to the end of the screen.
            'J' -> {
                if (vt52Mode) {
                    terminal.getDocument().eraseInDisplay(0)
                    if (log.isDebugEnabled) {
                        log.debug("Erase from the cursor to the end of the screen.")
                    }
                } else {
                    if (log.isWarnEnabled) {
                        log.warn("ESC J     Erase from the cursor to the end of the screen.. (Not VT52 Mode)")
                    }
                }


            }


            // ESC E    Next Line (NEL  is 0x85).
            'E' -> {
                val oldPosition = terminal.getCursorModel().getPosition()
                terminal.getDocument().newline()
                terminal.getCursorModel().move(CursorMove.RowHome)
                val newPosition = terminal.getCursorModel().getPosition()

                if (log.isDebugEnabled) {
                    log.debug("Next Line (NEL  is 0x85). old: $oldPosition , new: $newPosition")
                }

            }

            // Full Reset (RIS), VT100.
            'c' -> {
                // reset graphic
                terminalModel.setData(DataKey.GraphicCharacterSet, GraphicCharacterSet())

                // reset style
                terminalModel.setData(DataKey.TextStyle, TextStyle())


                // reset scrolling region
                terminalModel.setData(DataKey.ScrollingRegion, ScrollingRegion(1, terminalModel.getRows()))


                terminalModel.setData(DataKey.SGRMouseMode, false)
                terminalModel.setData(DataKey.AutoNewline, false)
                terminalModel.setData(DataKey.ReverseVideo, false)
                terminalModel.setData(DataKey.OriginMode, false)
                terminalModel.setData(DataKey.AutoWrapMode, false)

                // reset caret
                terminal.getCursorModel().move(1, 1)

                // clear screen
                terminal.getDocument().eraseInDisplay(2)

                // scroll to bottom
                terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)


                if (log.isDebugEnabled) {
                    log.debug("Full Reset (RIS).")
                }


            }

            // ESC n    Invoke the G2 Character Set as GL (LS2).
            'n' -> {
                terminalModel.getData(DataKey.GraphicCharacterSet).use(Graphic.G2)
                if (log.isDebugEnabled) {
                    log.debug("Use Graphic.G2")
                }

            }


            // ESC o    Invoke the G3 Character Set as GL (LS3).
            'o' -> {
                terminalModel.getData(DataKey.GraphicCharacterSet).use(Graphic.G3)
                if (log.isDebugEnabled) {
                    log.debug("Use Graphic.G3")
                }

            }

            // ESC 7     Save Cursor (DECSC), VT100.
            '7' -> {
                CursorStoreStores.store(terminal)
            }

            // Restore Cursor (DECRC), VT100.
            '8' -> {
                CursorStoreStores.restore(terminal)
            }


            // Two Char Sequence
            '#',
            ControlCharacters.SP,
            '%' -> {
                processTwoCharSequence(ch, reader.read())
            }

            // String Terminator (ST  is 0x9c).
            '\\' -> {
                if (log.isWarnEnabled) {
                    log.warn("String Terminator (ST  is 0x9c).")
                }
            }

            else -> {
                if (log.isErrorEnabled) {
                    log.error("Unexpected ESC character: $ch")
                }
            }
        }

        return state
    }


    private fun processTwoCharSequence(first: Char, second: Char) {
        if (first == '#') {
            // DEC Screen Alignment Test (DECALN), VT100.
            if (second.digitToInt() == 8) {
                val cursorModel = terminal.getCursorModel()
                val document = terminal.getDocument()
                val region = terminalModel.getData(DataKey.ScrollingRegion)
                val caretPosition = cursorModel.getPosition()
                val cols = terminalModel.getCols()
                for (i in 1..region.bottom) {
                    cursorModel.move(i, 1)
                    document.write("E".repeat(cols))
                }
                cursorModel.move(row = caretPosition.y, col = caretPosition.x)
                if (log.isDebugEnabled) {
                    log.debug("DEC Screen Alignment Test (DECALN)")
                }
            } else if (log.isWarnEnabled) {
                log.warn("Escape DEC Screen Alignment Test (WARN). Unknown: $second")
            }
        } else if (first == '%') { // ESC % @ or ESC % G
            if (log.isWarnEnabled) {
                if (second == '@') {
                    log.warn("Select default character set.  That is ISO 8859-1 (ISO 2022).")
                } else if (second == 'G') {
                    log.warn("Select UTF-8 character set, ISO 2022.")
                }
            }
        } else if (first == ControlCharacters.SP) {
            val graphicCharacterSet = terminal.getTerminalModel().getData(DataKey.GraphicCharacterSet)

            when (second) {
                'F' -> {
                    if (log.isErrorEnabled) {
                        log.error("7-bit controls (S7C1T), VT220.")
                    }
                }

                'G' -> {
                    if (log.isErrorEnabled) {
                        log.error("8-bit controls (S8C1T), VT220.")
                    }
                }

                //  Set ANSI conformance level 1, ECMA-43.
                'L' -> {
                    graphicCharacterSet.designate(Graphic.G1, CharacterSet.USASCII)
                    if (log.isDebugEnabled) {
                        log.debug("Set ANSI conformance level 1, ECMA-43.")
                    }
                }


                //  Set ANSI conformance level 2, ECMA-43.
                'M' -> {
                    graphicCharacterSet.designate(Graphic.G2, CharacterSet.USASCII)
                    if (log.isDebugEnabled) {
                        log.debug("Set ANSI conformance level 2, ECMA-43.")
                    }
                }

                // Set ANSI conformance level 3, ECMA-43.
                'N' -> {
                    graphicCharacterSet.designate(Graphic.G3, CharacterSet.USASCII)
                    if (log.isDebugEnabled) {
                        log.debug("Set ANSI conformance level 3, ECMA-43.")
                    }
                }

                else -> {
                    if (log.isWarnEnabled) {
                        log.warn("Unknown character set: $second")
                    }
                }
            }
        } else if (log.isErrorEnabled) {
            log.error("Unexpected ESC character first: $first , second: $second")
        }

    }
}