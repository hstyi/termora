package app.termora.terminal

import java.text.BreakIterator
import kotlin.math.max


class TerminalLine {

    private val chars = TCharArrayList()
    private val styles = TTextStyleArrayList()

    var wrapped = false

    constructor()
    constructor(chars: List<Pair<Char, TextStyle>>) {
        addChars(chars)
    }

    fun addChar(char: Pair<Char, TextStyle>) {
        chars.add(char.first)
        styles.add(char.second)
    }

    fun addChars(chars: List<Pair<Char, TextStyle>>) {
        for (e in chars) {
            this.chars.add(e.first)
            this.styles.add(e.second)
        }
    }

    fun chars(): List<Pair<Char, TextStyle>> {
        val list = mutableListOf<Pair<Char, TextStyle>>()
        for (i in 0 until this.chars.size()) {
            list.add(Pair(this.chars.get(i), this.styles.getTextStyle(i)))
        }
        return list
    }

    fun getText(): String {
        val sb = StringBuilder()

        if (chars.isEmpty() || chars.get(0).isNull) {
            return sb.toString()
        }

        for (i in chars.size() - 1 downTo 0) {
            if (chars.get(i).isNull) {
                continue
            }

            for (j in 0..i) {
                val ch = chars.get(j)
                if (ch.isNull) {
                    sb.append(Char.Space)
                } else if (ch.isSoftHyphen) {
                    continue
                } else {
                    sb.append(ch)
                }
            }

            break
        }

        return sb.toString()
    }


    /**
     * 写入字符
     */
    fun write(offset: Int, buffer: CharBuffer) {
        modifyChars(
            offset,
            buffer.size,
            buffer
        )
    }


    /**
     * @param offset 从哪一个元素的哪一个下标开始
     */
    private fun modifyChars(offset: Int, count: Int, buffer: CharBuffer) {
        for (i in 0 until offset) {
            if (chars.size() > i) {
                if (chars.get(i).isNull) {
                    chars.set(i, Char.Space)
                }
            } else {
                break
            }
        }

        for (i in 0 until count) {
            val index = offset + i
            if (index >= chars.size()) {
                chars.add(buffer.chars[i])
                styles.add(buffer.style)
            } else {
                chars.set(index, buffer.chars[i])
                styles.set(index, buffer.style)
            }
        }
    }


    private fun segments(): List<Pair<String, TextStyle>> {
        val list = mutableListOf<Pair<StringBuilder, TextStyle>>()
        for (i in 0 until chars.size()) {
            val e = Pair(chars.get(i), styles.getTextStyle(i))
            if (list.isEmpty()) {
                list.add(Pair(StringBuilder(), e.second))
            }
            if (e.second != list.last().second) {
                list.add(Pair(StringBuilder(), e.second))
            }
            list.last().first.append(if (e.first.isNull) Char.Space else e.first)
        }
        return list.map { Pair(it.first.toString(), it.second) }
    }

    fun characters(): MutableList<Triple<String, TextStyle, Int>> {
        val characters = mutableListOf<Triple<String, TextStyle, Int>>()
        val breakIterator = BreakIterator.getCharacterInstance()
        for (e in segments()) {
            breakIterator.setText(e.first)
            var start = breakIterator.first()
            var end = breakIterator.next()
            while (end != BreakIterator.DONE) {
                val grapheme = e.first.substring(start, end).dropWhile { it == Char.SoftHyphen }
                if (grapheme.isNotEmpty()) {
                    var width = 0
                    for (char in grapheme.chars()) {
                        width += max(1, mk_wcwidth(char))
                    }
                    characters.add(Triple(grapheme, e.second, width))
                }
                start = end
                end = breakIterator.next()
            }
        }
        return characters
    }

    /**
     * 删除字符，删除字符之后，后面的元素会向前推进。
     *
     * @see [eraseChars]
     */
    fun deleteChars(offset: Int, count: Int, c: Pair<Char, TextStyle>) {
        for (i in 0 until count) {
            chars.remove(offset)
            styles.remove(offset)

            chars.add(c.first)
            styles.add(c.second)
        }
    }

    /**
     * 擦除字符，如果擦除的字符原本有字符那么就用空格代替。如果一旦超出原有的字符的长度，那么只保留样式
     */
    fun eraseChars(offset: Int, count: Int, attr: TextStyle) {

        eraseChars(
            offset,
            count,
            Pair(if (count >= actualCount()) Char.Null else Char.Space, attr)
        )
    }

    /**
     * 擦除字符，如果擦除的字符原本有字符那么就用空格代替。如果一旦超出原有的字符的长度，那么只保留样式
     */
    fun eraseChars(offset: Int, count: Int, c: Pair<Char, TextStyle>) {
        for (i in 0 until count) {
            if (offset + i >= chars.size()) {
                break
            }
            chars.set(offset + i, c.first)
            styles.set(offset + i, c.second)
        }
    }

    /**
     * 插入字符
     */
    fun insertChars(offset: Int, cols: Int, buffer: CharBuffer) {
        for (i in 0 until buffer.size) {
            chars.insert(i + offset, buffer.chars[i])
            styles.insert(i + offset, buffer.style)
            if (chars.size() > cols) {
                chars.remove(chars.size() - 1)
                styles.remove(styles.size() - 1)
            }
        }
    }

    fun actualCount(): Int {
        for (i in chars.size() - 1 downTo 0) {
            if (!chars.get(i).isNull) {
                return i + 1
            }
        }
        return 0
    }

    override fun toString(): String {
        return getText()
    }

}

class CharBuffer : Iterable<Char> {

    companion object {
        private val breakIterator = BreakIterator.getCharacterInstance()
    }

    val size get() = chars.size
    val chars: CharArray
    val style: TextStyle

    constructor(chars: CharArray, style: TextStyle) {
        this.style = style
        this.chars = chars
    }

    constructor(text: String, style: TextStyle) {
        this.style = style

        val chars = mutableListOf<Char>()
        breakIterator.setText(text)
        var start = breakIterator.first()
        var end = breakIterator.next()
        while (end != BreakIterator.DONE) {
            val ch = text.substring(start, end)
            chars.addAll(ch.toCharArray().toList())
            if (ch.length == 1 && mk_wcwidth(ch.first()) == 2) {
                chars.add(Char.SoftHyphen)
            }
            start = end
            end = breakIterator.next()
        }

        this.chars = chars.toCharArray()
    }

    fun chunked(offset: Int, length: Int): CharBuffer {
        return CharBuffer(chars.copyOfRange(offset, offset + length), style)
    }

    fun isEmpty(): Boolean {
        return size == 0
    }

    override fun iterator(): Iterator<Char> {
        return chars.iterator()
    }

}
