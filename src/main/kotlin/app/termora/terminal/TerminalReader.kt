package app.termora.terminal

import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class TerminalReader {
    private val buffer = ArrayDeque<Char>()


    fun addLast(char: Char) {
        buffer.addLast(char)
    }

    fun addFirst(chars: List<Char>) {
        for (i in chars.size - 1 downTo 0) {
            addFirst(chars[i])
        }
    }


    fun addLast(chars: List<Char>) {
        buffer.addAll(chars)
    }

    fun addFirst(ch: Char) {
        buffer.addFirst(ch)
    }

    fun addLast(text: String) {
        text.forEach { addLast(it) }
    }

    fun read(): Char {
        return buffer.removeFirst()
    }

    fun peek(): Char? {
        return buffer.peekFirst()
    }

    fun isEmpty(): Boolean {
        return buffer.isEmpty()
    }

    fun isNotEmpty(): Boolean {
        return buffer.isNotEmpty()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (c in buffer) {
            when (c) {
                ControlCharacters.TAB -> sb.append("TAB")
                ControlCharacters.ESC -> sb.append("ESC")
                ControlCharacters.BEL -> sb.append("BEL")
                ControlCharacters.CR -> sb.append("CR")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun clear() {
        buffer.clear()
    }


}