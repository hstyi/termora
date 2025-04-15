package app.termora.highlight

import app.termora.ApplicationScope
import app.termora.terminal.*
import app.termora.terminal.panel.TerminalDisplay
import app.termora.terminal.panel.TerminalPaintListener
import app.termora.terminal.panel.TerminalPanel
import org.slf4j.LoggerFactory
import java.awt.Graphics
import kotlin.math.min
import kotlin.random.Random

class KeywordHighlightPaintListener private constructor() : TerminalPaintListener {

    companion object {
        fun getInstance(): KeywordHighlightPaintListener {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeywordHighlightPaintListener::class) { KeywordHighlightPaintListener() }
        }

        private val tag = Random.nextInt()
        private val log = LoggerFactory.getLogger(KeywordHighlightPaintListener::class.java)
    }

    private val keywordHighlightManager get() = KeywordHighlightManager.getInstance()

    override fun before(
        offset: Int,
        count: Int,
        g: Graphics,
        terminalPanel: TerminalPanel,
        terminalDisplay: TerminalDisplay,
        terminal: Terminal
    ) {
        for (highlight in keywordHighlightManager.getKeywordHighlights()) {
            if (!highlight.enabled) {
                continue
            }

            val document = terminal.getDocument()
            val kinds = mutableListOf<FindKind>()
            val iterator = object : Iterator<TerminalLine> {
                private var index = offset + 1
                private val maxCount = min(index + count, document.getLineCount())
                override fun hasNext(): Boolean {
                    return index <= maxCount
                }

                override fun next(): TerminalLine {
                    return document.getLine(index++)
                }
            }

            if (highlight.regex) {
                try {
                    val regex = if (highlight.matchCase)
                        highlight.keyword.toRegex()
                    else highlight.keyword.toRegex(RegexOption.IGNORE_CASE)
                    RegexFinder(regex, iterator).find()
                        .apply { kinds.addAll(this) }
                } catch (e: Exception) {
                    if (log.isDebugEnabled) {
                        log.error(e.message, e)
                    }
                }
            } else {
                SubstrFinder(iterator, CharArraySubstr(highlight.keyword.toCharArray())).find(!highlight.matchCase)
                    .apply { kinds.addAll(this) }
            }

            for (kind in kinds) {
                terminal.getMarkupModel().addHighlighter(
                    KeywordHighlightHighlighter(
                        HighlighterRange(
                            kind.startPosition.copy(y = kind.startPosition.y + offset),
                            kind.endPosition.copy(y = kind.endPosition.y + offset)
                        ),
                        terminal = terminal,
                        keywordHighlight = highlight,
                    )
                )
            }

        }

    }

    override fun after(
        offset: Int,
        count: Int,
        g: Graphics,
        terminalPanel: TerminalPanel,
        terminalDisplay: TerminalDisplay,
        terminal: Terminal
    ) {
        terminal.getMarkupModel().removeAllHighlighters(tag)
    }

    private class RegexFinder(
        private val regex: Regex,
        private val iterator: Iterator<TerminalLine>
    ) {
        private data class Coords(val row: Int, val col: Int)
        private data class MatchResultWithCoords(
            val match: String,
            val coords: List<Coords>
        )

        fun find(): List<FindKind> {

            val lines = mutableListOf<TerminalLine>()
            val kinds = mutableListOf<FindKind>()

            for ((index, line) in iterator.withIndex()) {

                lines.add(line)
                if (line.wrapped) continue

                val data = mutableListOf<MutableList<Char>>()
                for (e in lines) {
                    data.add(mutableListOf())
                    for (c in e.chars()) {
                        if (c.first.isNull) break
                        data.last().add(c.first)
                    }
                }

                lines.clear()

                val resultWithCoords = findMatchesWithCoords(data)
                if (resultWithCoords.isEmpty()) continue
                val offset = index - data.size + 1

                for (e in resultWithCoords) {
                    val coords = e.coords
                    if (coords.isEmpty()) continue
                    kinds.add(
                        FindKind(
                            startPosition = Position(coords.first().row + offset + 1, coords.first().col + 1),
                            endPosition = Position(coords.last().row + offset + 1, coords.last().col + 1)
                        )
                    )
                }
            }

            return kinds
        }

        private fun findMatchesWithCoords(data: List<List<Char>>): List<MatchResultWithCoords> {
            val flatChars = StringBuilder()
            val indexMap = mutableListOf<Coords>()

            // 拉平成字符串，并记录每个字符的位置
            for ((rowIndex, row) in data.withIndex()) {
                for ((colIndex, char) in row.withIndex()) {
                    flatChars.append(char)
                    indexMap.add(Coords(rowIndex, colIndex))
                }
            }

            return regex.findAll(flatChars.toString())
                .map { MatchResultWithCoords(it.value, indexMap.subList(it.range.first, it.range.last + 1)) }
                .toList()
        }
    }


    private class KeywordHighlightHighlighter(
        range: HighlighterRange, terminal: Terminal,
        val keywordHighlight: KeywordHighlight
    ) : TagHighlighter(range, terminal, tag) {
        override fun getTextStyle(position: Position, textStyle: TextStyle): TextStyle {
            return textStyle.copy(
                foreground = keywordHighlight.textColor,
                background = keywordHighlight.backgroundColor,
                bold = keywordHighlight.bold,
                italic = keywordHighlight.italic,
                underline = keywordHighlight.underline,
                lineThrough = keywordHighlight.lineThrough,
            )
        }
    }
}


