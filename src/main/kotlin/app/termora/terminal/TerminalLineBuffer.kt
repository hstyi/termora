package app.termora.terminal

import org.slf4j.LoggerFactory
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class TerminalLineBuffer(
    private val terminal: Terminal,
    val isAlternateScreenBuffer: Boolean
) {
    /**
     * 当前可视区域的行
     */
    private val screen: MutableList<TerminalLine> = mutableListOf()

    // AlternateScreenBuffer 模式没有历史
    // history lines
    private val buffer = StrangeArrayList()

    private val cursorModel get() = terminal.getCursorModel()
    private val terminalModel get() = terminal.getTerminalModel()
    private val currentTextStyle get() = terminalModel.getData(DataKey.TextStyle)
    private val scrollingModel get() = terminal.getScrollingModel()
    private var resizing = false

    companion object {
        private val log = LoggerFactory.getLogger(TerminalLineBuffer::class.java)
    }

    private inner class StrangeArrayList : ArrayList<TerminalLine>() {
        override fun addAll(elements: Collection<TerminalLine>): Boolean {
            return canIUse { super.addAll(elements) } ?: false
        }

        override fun add(element: TerminalLine): Boolean {
            return canIUse { super.add(element) } ?: false
        }

        override fun addFirst(element: TerminalLine) {
            canIUse { super.addFirst(element) }
        }

        override fun addLast(element: TerminalLine) {
            canIUse { super.addLast(element) }
        }

        override fun add(index: Int, element: TerminalLine) {
            canIUse { super.add(index, element) }
        }

        override fun addAll(index: Int, elements: Collection<TerminalLine>): Boolean {
            return canIUse { super.addAll(index, elements) } ?: false
        }

        private inline fun <R> canIUse(block: () -> R): R? {
            if (isAlternateScreenBuffer) return null
            return block.invoke().apply {
                if (!resizing) {
                    while (size > terminalModel.getMaxRows()) {
                        removeFirst()
                        // 超出的行数，前 N 行已经被清理
                        terminal.getTerminalModel().setData(DocumentImpl.OverflowLines, 1)
                    }
                }
            }
        }
    }

    private val currentTerminalLine: TerminalLine
        get() {
            val y = cursorModel.getPosition().y - 1
            while (y >= screen.size) {
                grow(screen)
            }
            return screen[y]
        }

    fun getText(): String {
        val sb = StringBuilder()
        val count = getLineCount()
        for (i in 0 until count) {
            sb.append(getLineAt(i).getText())
            if (i != count - 1) {
                sb.append(ControlCharacters.LF)
            }
        }
        return sb.toString()
    }

    fun eraseInDisplay(n: Int) {
        val position = cursorModel.getPosition()
        val cols = terminalModel.getCols()
        val attr = terminalModel.getData(DataKey.TextStyle).copyOnlyColors()

        when (n) {
            // 清除从光标开始到屏幕结尾的数据
            0 -> {
                for (i in position.y..max(screen.size, position.y)) {
                    val line = getScreenLineAt(i - 1, attr)
                    if (i == position.y) {
                        line.eraseChars(position.x - 1, cols - (position.x - 1), attr)
                    } else {
                        line.eraseChars(0, cols, attr)
                    }
                }
            }
            //  清除从光标位置到行头
            1 -> {
                for (i in 1..position.y) {
                    val line = getScreenLineAt(i - 1)
                    val c = Pair(Char.Space, attr)
                    if (i == position.y) {
                        line.eraseChars(0, position.x, c)
                    } else {
                        line.eraseChars(0, cols, c)
                    }
                }
            }

            // 清除当前屏幕
            2 -> {

                // 删除最下面的空行，不然会把空行也添加到了历史
                while (screen.isNotEmpty()) {
                    if (screen.last().actualCount() < 1) {
                        screen.removeLast()
                    } else {
                        break
                    }
                }

                buffer.addAll(screen)
                screen.clear()

                // 重新渲染屏幕
                for (i in 0 until terminalModel.getRows()) {
                    grow(screen, attr)
                }

                if (terminal.getScrollingModel().isStick()) {
                    terminal.getScrollingModel().scrollTo(Int.MAX_VALUE)
                }
            }

            // 清除所有缓冲区
            3 -> {
                buffer.clear()
                screen.clear()
                terminal.getScrollingModel().scrollTo(0)
                cursorModel.move(1, 1)
                terminal.getMarkupModel().removeAllHighlighters()
            }
        }

    }


    private fun grow(lines: MutableList<TerminalLine>, attr: TextStyle = TextStyle.Default) {
        lines.add(newline(attr))
    }

    private fun newline(attr: TextStyle = TextStyle.Default): TerminalLine {
        val line = TerminalLine()
        for (i in 0 until terminalModel.getCols()) {
            line.addChar(Pair(Char.Null, attr))
        }
        return line
    }

    private fun wrap() {
        if (cursorModel.getPosition().x > terminalModel.getCols()) {
            if (terminalModel.getData(DataKey.AutoWrapMode, true)) {
                // 当前行设置为换行
                currentTerminalLine.wrapped = true
                // 开新行
                terminal.getDocument().newline()
                cursorModel.move(CursorMove.RowHome)
            } else {
                cursorModel.move(CursorMove.RowHome)
            }
        }

    }

    fun write(text: String) {
        // new line
        if (screen.isEmpty()) {
            grow(screen)
        }

        // 尝试换行
        wrap()

        var buffer = CharBuffer(Normalizer.normalize(text, Normalizer.Form.NFC), currentTextStyle)

        // 如果超出了
        while (buffer.size - 1 + cursorModel.getPosition().x > terminalModel.getCols()) {

            // 剩余空间
            val count = terminalModel.getCols() - cursorModel.getPosition().x + 1


            // 分割
            writeTerminalLineChar(buffer.chunked(0, count))

            // 尝试换行
            wrap()

            // 分割剩余部分
            buffer = buffer.chunked(count, buffer.size - count)

        }


        // 写入字符
        writeTerminalLineChar(buffer)

    }

    private fun writeTerminalLineChar(buffer: CharBuffer) {

        if (buffer.isEmpty()) {
            return
        }

        val x = cursorModel.getPosition().x

        // 插入模式
        if (terminalModel.getData(DataKey.InsertMode, false)) {
            currentTerminalLine.insertChars(x - 1, terminalModel.getCols(), buffer)
        } else {
            currentTerminalLine.write(x - 1, buffer)
        }

        // 移动 N 次
        cursorModel.move(CursorMove.Right, buffer.size)

    }

    fun getLineAt(index: Int): TerminalLine {
        if (index < buffer.size) {
            return buffer[index]
        }
        return getScreenLineAt(index - buffer.size)
    }

    /**
     * 如果一旦超出，则扩容
     */
    fun getScreenLineAt(index: Int, attr: TextStyle = TextStyle.Default): TerminalLine {
        while (index >= screen.size) {
            grow(screen, attr = attr)
        }
        return screen[index]
    }

    fun eraseInLine(n: Int) {
        val x = cursorModel.getPosition().x - 1
        val y = cursorModel.getPosition().y
        val cols = terminal.getTerminalModel().getCols()
        val attr = terminalModel.getData(DataKey.TextStyle).copyOnlyColors()
        val line = getScreenLineAt(y - 1)

        when (n) {
            // 清除从光标位置到该行末尾的部分
            0 -> {
                line.eraseChars(x, cols - x, Pair(Char.Null, attr))
                line.wrapped = false
                if (log.isDebugEnabled) {
                    log.debug("Erase In Line 0. x:$x , y:$y cols:$cols")
                }
            }

            // 清除从光标位置到该行开头的部分
            1 -> {
                line.eraseChars(0, cursorModel.getPosition().x, attr = attr)
                if (log.isDebugEnabled) {
                    log.debug("Erase In Line 1. x:$x , y:$y , cols:$cols")
                }
            }

            // 清除整行。光标位置不变
            2 -> {
                line.eraseChars(0, cols, attr = attr)
                if (log.isDebugEnabled) {
                    log.debug("Erase In Line 2. x:$x , y:$y , cols:$cols")
                }
            }
        }
    }

    fun getLineCount(): Int {
        return screen.size + buffer.size
    }

    private fun getScreenSizeIgnoreNullLine(): Int {
        if (screen.isEmpty()) {
            return 0
        }

        for (i in screen.size - 1 downTo 0) {
            if (screen[i].actualCount() == 0) {
                continue
            }
            return i + 1
        }

        return 0
    }


    /**
     * 添加行
     * @param top 固定头部的索引，从1开始。
     * @param bottom 固定底部的索引，从1开始。
     * @param count 添加多少行，从 top - 1 开始加，然后在 [bottom] 位置开始删。因为总量要保持不变
     */
    fun insertLines(top: Int, bottom: Int, count: Int) {

        val tail = mutableListOf<TerminalLine>()
        for (i in 0 until screen.size - bottom) {
            tail.addFirst(screen.removeLast())
        }

        val head = mutableListOf<TerminalLine>()
        for (i in 1 until top) {
            head.addLast(screen.removeFirst())
        }

        for (i in 0 until count) {
            screen.addFirst(newline(terminalModel.getData(DataKey.TextStyle)))
        }

        for (i in 0 until count) {
            screen.removeLast()
        }

        screen.addAll(0, head)
        screen.addAll(tail)

    }

    /**
     * 删除行
     * @param top 固定头部的索引，从1开始。
     * @param bottom 固定底部的索引，从1开始。
     * @param count 删除多少行，从 top - 1 开始删，然后在 [bottom] 位置补回来
     */
    fun deleteLines(top: Int, bottom: Int, count: Int) {
        val tail = mutableListOf<TerminalLine>()
        for (i in 0 until screen.size - bottom) {
            if (screen.isEmpty()) break
            tail.addFirst(screen.removeLast())
        }

        val head = mutableListOf<TerminalLine>()
        for (i in 1 until top) {
            if (screen.isEmpty()) break
            head.addLast(screen.removeFirst())
        }

        val removed = mutableListOf<TerminalLine>()
        for (i in 0 until count) {
            if (screen.isEmpty()) break
            removed.add(screen.removeFirst())
        }

        screen.addAll(0, head)
        for (i in 0 until count) {
            screen.add(newline(terminalModel.getData(DataKey.TextStyle)))
        }
        screen.addAll(tail)

        if (top != 1 || isAlternateScreenBuffer) {
            return
        }

        buffer.addAll(removed)

    }

    /**
     * 将 x y 转换成字符索引
     */
    private fun positionToCharIndex(lines: Iterator<TerminalLine>, positions: List<Position>): IntArray {
        val charIndexes = IntArray(positions.size, init = { -1 })
        val status = BooleanArray(positions.size, init = { false })
        positions.forEachIndexed { index, position -> status[index] = !position.isValid() }

        // 如果全都是非法坐标，那么直接返回
        if (status.all { it }) {
            return charIndexes
        }

        charIndexes.fill(0, 0, charIndexes.size)

        val iterator = object : Iterator<TerminalLine> {
            private val line = newline().apply {
                write(0, CharBuffer(CharArray(terminalModel.getCols()) { Char.Space }, TextStyle.Default))
            }

            override fun hasNext(): Boolean {
                return true
            }

            override fun next(): TerminalLine {
                return if (lines.hasNext()) lines.next() else line
            }

        }

        for ((i, line) in iterator.withIndex()) {
            var breakFlag = true

            for ((j, position) in positions.withIndex()) {
                if (status[j]) {
                    continue
                }
                if (i + 1 == position.y) {
                    charIndexes[j] = charIndexes[j] + position.x - 1
                    status[j] = true
                } else {
                    charIndexes[j] += line.actualCount()
                    if (breakFlag) {
                        breakFlag = false
                    }
                }
            }

            if (breakFlag) {
                break
            }

        }


        return charIndexes
    }


    /**
     * 将字符索引转化成 x y
     */
    private fun charIndexToPosition(lines: Iterator<TerminalLine>, charIndexes: IntArray): Array<Position> {
        val positions = Array(charIndexes.size, init = { Position.unknown })
        val indexes = IntArray(charIndexes.size, init = { 0 })
        val infinity = object : Iterator<TerminalLine> {
            private val line = TerminalLine(listOf())
            private var index = 0

            override fun hasNext(): Boolean {
                return index < terminalModel.getMaxRows()
            }

            override fun next(): TerminalLine {
                index++
                return if (lines.hasNext()) lines.next() else line
            }
        }

        for ((i, line) in infinity.withIndex()) {
            var breakFlag = true
            for ((j, charIndex) in charIndexes.withIndex()) {
                if (charIndex < 0 || positions[j] != Position.unknown) {
                    continue
                }
                if (line.actualCount() + indexes[j] >= charIndex) {
                    for (k in 0 until line.actualCount()) {
                        if (indexes[j] == charIndex) {
                            // 如果超出了列宽，那么就是下一行
                            if (k + 1 > terminalModel.getCols()) {
                                break
                            }
                            positions[j] = Position(y = i + 1, x = k + 1)
                            break
                        }
                        indexes[j]++
                    }
                    // 如果等于 unknown 表示还没有找到，继续找
                    breakFlag = positions[j] != Position.unknown
                } else {
                    indexes[j] += line.actualCount()
                    breakFlag = false
                }
            }
            if (breakFlag) {
                break
            }
        }

        return positions
    }

    private fun lineIterator(): Iterator<TerminalLine> {
        return object : Iterator<TerminalLine> {
            private var index = 0
            override fun hasNext(): Boolean {
                return index < getLineCount()
            }

            override fun next(): TerminalLine {
                return getLineAt(index++)
            }
        }
    }


    fun resize(oldSize: TerminalSize, newSize: TerminalSize) {
        val lineCount = getLineCount()

        if (lineCount < 1 || isAlternateScreenBuffer) {
            return
        }

        val scrollingModel = scrollingModel.getNonAlternateScreenBufferScrollingModel()

        // 滚动条位置
        val verticalScrollOffset = scrollingModel.getVerticalScrollOffset()
        val maxVerticalScrollOffset = scrollingModel.getMaxVerticalScrollOffset()

        // 选择的位置
        var selectionStartPosition = terminal.getSelectionModel().getSelectionStartPosition()
        var selectionEndPosition = terminal.getSelectionModel().getSelectionEndPosition()

        // 如果是选中了整行，那么这里指定一下最后一行的字符数量。如果不指定字符索引可能会计算错误
        if (selectionEndPosition.isValid() && selectionEndPosition.y <= lineCount) {
            val charCount = getLineAt(selectionEndPosition.y - 1).actualCount()
            if (selectionEndPosition.x > charCount) {
                selectionEndPosition = selectionEndPosition.copy(x = charCount)
            }
        }

        // 如果开始坐标选中了空的地方，那么判断是否有下一行，如果有从下一行的第一列开始选。
        // 如果没有下一行，则从开始行的第一列开始选
        if (selectionStartPosition.isValid() && selectionStartPosition.y <= lineCount) {
            val charCount = getLineAt(selectionStartPosition.y - 1).actualCount()
            if (selectionStartPosition.x > charCount) {
                selectionStartPosition = if (selectionStartPosition.y + 1 <= lineCount) {
                    selectionStartPosition.copy(x = 1, y = selectionStartPosition.y + 1)
                } else {
                    selectionStartPosition.copy(x = 1)
                }
            }
        }

        // 坐标转字符索引
        val charIndexes = positionToCharIndex(lineIterator(), listOf(selectionStartPosition, selectionEndPosition))

        // resize
        Resizer(oldSize, newSize).resize()

        // 滚动位置不变
        scrollingModel.scrollTo(
            scrollingModel.getMaxVerticalScrollOffset()
                    - (maxVerticalScrollOffset - verticalScrollOffset)
        )

        // 字符索引转坐标
        val positions = charIndexToPosition(lineIterator(), charIndexes)
        if (positions[0].isValid() && positions[1].isValid()) {
            // 如果超过了行数，那么选择的就是空行，空行是不会换行的，所以保持原样
            if (selectionStartPosition.y > lineCount) {
                positions[0] = selectionStartPosition
            }
            if (selectionEndPosition.y > lineCount) {
                positions[1] = selectionEndPosition
            }
            terminal.getSelectionModel().setSelection(positions[0], positions[1])
        }

    }

    fun getBufferCount(): Int {
        return buffer.size
    }


    private inner class Resizer(val oldSize: TerminalSize, val newSize: TerminalSize) {
        private var myLines = ArrayList<TerminalLine>()
        val count get() = myLines.size
        private val oldScreenSize = getScreenSizeIgnoreNullLine()

        private fun addLines(iterator: Iterator<TerminalLine>): MutableList<TerminalLine> {
            val list = myLines
            val segments = mutableListOf<TerminalLine>()
            var count = 0
            val cols = newSize.cols

            while (iterator.hasNext()) {
                do {
                    val current = iterator.next()
                    segments.add(current)
                    count += current.actualCount()
                } while (current.wrapped && iterator.hasNext())

                // 多行合并成一行
                if (count <= cols) {
                    val line = segments.removeFirst().apply { wrapped = false }
                    if (segments.isNotEmpty()) {
                        segments.forEach {
                            line.addChars(it.chars())
                        }
                    }
                    list.add(line)
                } else { // 拆分成多行
                    val pages = if (count % cols == 0) count / cols else count / cols + 1
                    val chars = mutableListOf<Pair<Char, TextStyle>>()

                    for (segment in segments) {
                        chars.addAll(segment.chars().subList(0, segment.actualCount()))
                    }

                    for (i in 0 until pages) {
                        val line = TerminalLine(
                            chars.subList(
                                i * cols,
                                min(i * cols + cols, chars.size)
                            )
                        ).apply { wrapped = true }
                        list.add(line)
                    }

                    // 最后一个不换行
                    list.last().wrapped = false
                }

                count = 0
                segments.clear()
            }

            /*for (e in list) {
                if (e.chars.size < cols) {
                    for (i in 0 until cols - e.chars.size) {
                        e.chars.addLast(Pair(Char.Null, e.chars.last().second))
                    }
                }
            }*/

            return list
        }

        fun sublist(offset: Int, count: Int): List<TerminalLine> {
            return myLines.subList(offset, offset + count)
        }

        fun resize() {
            resizing = true

            // 修改列，处理换行
            resizeCols()

            // 修改行，处理可视区域
            resizeRows()

            resizing = false
        }

        private fun resizeCols() {
            // 屏幕行开始的位置
            var lineStartIndex = -1
            val screenSize = oldScreenSize
            val totalCount = buffer.size + screenSize

            if (totalCount < 1) {
                return
            }

            // 开始处理换行
            addLines(object : Iterator<TerminalLine> {
                private var index = 0
                override fun hasNext(): Boolean {
                    return index < totalCount
                }

                override fun next(): TerminalLine {
                    val line = if (index < buffer.size) buffer[index]
                    else screen[index - buffer.size]

                    // 如果一旦超过历史缓冲区，那么就等于开始处理当前屏幕的了，也就是可视范围内的
                    if (lineStartIndex == -1 && index >= buffer.size) {
                        lineStartIndex = count
                    }


                    index++

                    return line
                }

            })


            // 清空历史缓冲区
            buffer.clear()

            val resizeLines = sublist(lineStartIndex, count - lineStartIndex)
            // 如果大于之前的，那么肯定换行了
            if (resizeLines.size > screenSize) {
                // 只有超出可视范围才需要进入缓冲区
                if (resizeLines.size > newSize.rows) {
                    val count = resizeLines.size - screenSize
                    buffer.addAll(sublist(0, lineStartIndex))
                    buffer.addAll(resizeLines.subList(0, count))
                    screen.clear()
                    screen.addAll(resizeLines.subList(count, resizeLines.size))
                } else {
                    screen.clear()
                    screen.addAll(resizeLines)
                }
            } else if (resizeLines.size == screenSize) { // 如果等于，那么有可能也换行了但是没有开新行
                buffer.addAll(sublist(0, lineStartIndex))
                screen.clear()
                screen.addAll(resizeLines)
            } else { // 如果小于，那么肯定是之前换行的合并到一行了
                val count = abs(screenSize - resizeLines.size)
                if (lineStartIndex - count > 0) {
                    buffer.addAll(sublist(0, lineStartIndex - count))
                }
                screen.clear()
                if (lineStartIndex > 0) {
                    screen.addAll(sublist(max(lineStartIndex - count, 0), count))
                }
                screen.addAll(resizeLines)
            }
        }


        private fun resizeRows() {

            if (myLines.isEmpty()) {
                return
            }

            val screenSize = getScreenSizeIgnoreNullLine()
            // 大于表示换行了
            if (screenSize > oldScreenSize) {
                cursorModel.move(CursorMove.Down, screenSize - oldScreenSize)
            } else if (screenSize < oldScreenSize) { // 表示合并行了
                cursorModel.move(CursorMove.Up, oldScreenSize - screenSize)
            }

            if (newSize.rows == oldSize.rows) {
                return
            }

            // 高度缩小了
            if (newSize.rows < oldSize.rows) {
                // 如果可视行数小于可视区域无需处理
                if (screenSize < newSize.rows) {
                    return
                }
                val count = screenSize - newSize.rows
                for (i in 0 until count) {
                    buffer.add(screen.removeFirst())
                }
                cursorModel.move(CursorMove.Up, count)
            } else { // 高度增加了
                val count = min(newSize.rows - screenSize, buffer.size)
                if (count > 0) {
                    for (i in 0 until count) {
                        screen.addFirst(buffer.removeLast())
                    }
                    cursorModel.move(CursorMove.Down, count)
                }
            }
        }
    }
}


