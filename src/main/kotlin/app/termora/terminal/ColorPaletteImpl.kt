package app.termora.terminal

import app.termora.ApplicationScope
import java.awt.Color

interface ColorTheme {

    /**
     * @return rgb 如果返回 [Int.MAX_VALUE] 表示没有匹配到
     */
    fun getColor(color: TerminalColor): Int
}


open class DefaultColorTheme : ColorTheme {
    companion object {
        fun getInstance(): DefaultColorTheme {
            return ApplicationScope.forApplicationScope().getOrCreate(DefaultColorTheme::class) { DefaultColorTheme() }
        }
    }

    override fun getColor(color: TerminalColor): Int {
        when (color) {
            TerminalColor.Normal.BLACK -> return 0x000000
            TerminalColor.Normal.RED -> return 0xcd0000
            TerminalColor.Normal.GREEN -> return 0x00cd00
            TerminalColor.Normal.YELLOW -> return 0xcdcd00
            TerminalColor.Normal.BLUE -> return 0x1e90ff
            TerminalColor.Normal.MAGENTA -> return 0xcd00cd
            TerminalColor.Normal.CYAN -> return 0x00cdcd
            TerminalColor.Normal.WHITE -> return 0xe5e5e5
            TerminalColor.Bright.BLACK -> return 0x4c4c4c
            TerminalColor.Bright.RED -> return 0xff0000
            TerminalColor.Bright.GREEN -> return 0x00ff00
            TerminalColor.Bright.YELLOW -> return 0xffff00
            TerminalColor.Bright.BLUE -> return 0x4682b4
            TerminalColor.Bright.MAGENTA -> return 0xff00ff
            TerminalColor.Bright.CYAN -> return 0x00ffff
            TerminalColor.Bright.WHITE -> return 0xffffff
            TerminalColor.Basic.FOREGROUND -> return 0xffffff
            TerminalColor.Basic.BACKGROUND -> return Color.black.rgb
            TerminalColor.Basic.SELECTION_BACKGROUND -> return 0xc6dcfc
            TerminalColor.Basic.SELECTION_FOREGROUND -> return Color.black.rgb
            TerminalColor.Basic.HYPERLINK -> return 0x255ab4
            TerminalColor.Find.BACKGROUND -> return 0xffff00
            TerminalColor.Cursor.BACKGROUND -> return 0xc7c7c7
            else -> return 0
        }
    }
}

open class ColorPaletteImpl(private val terminal: Terminal) : ColorPalette {
    private val xterm256Colors = Array(240, init = { 0 })
    private val theme by lazy { DefaultColorTheme() }

    init {
        for (r in 0 until 6) {
            for (g in 0 until 6) {
                for (b in 0 until 6) {
                    val idx = 36 * r + 6 * g + b
                    xterm256Colors[idx] = Color(
                        getCubeColorValue(r),
                        getCubeColorValue(g),
                        getCubeColorValue(b),
                    ).rgb
                }
            }
        }

        for (gray in 0..23) {
            val a = 10 * gray + 8
            val idx = 216 + gray
            xterm256Colors[idx] = Color(a, a, a).rgb
        }
    }

    override fun getTerminal(): Terminal {
        return terminal
    }

    override fun getColor(color: TerminalColor): Int {
        return getTheme().getColor(color)
    }

    open fun getTheme(): ColorTheme {
        return theme
    }

    private fun getColor(index: Int): Int {

        return when (index) {
            0, 1 -> getColor(TerminalColor.Normal.BLACK)
            2 -> getColor(TerminalColor.Normal.RED)
            3 -> getColor(TerminalColor.Normal.GREEN)
            4 -> getColor(TerminalColor.Normal.YELLOW)
            5 -> getColor(TerminalColor.Normal.BLUE)
            6 -> getColor(TerminalColor.Normal.MAGENTA)
            7 -> getColor(TerminalColor.Normal.CYAN)
            8 -> getColor(TerminalColor.Normal.WHITE)

            9 -> getColor(TerminalColor.Bright.BLACK)
            10 -> getColor(TerminalColor.Bright.RED)
            11 -> getColor(TerminalColor.Bright.GREEN)
            12 -> getColor(TerminalColor.Bright.YELLOW)
            13 -> getColor(TerminalColor.Bright.BLUE)
            14 -> getColor(TerminalColor.Bright.MAGENTA)
            15 -> getColor(TerminalColor.Bright.CYAN)
            16 -> getColor(TerminalColor.Bright.WHITE)
            else -> xterm256Colors.getOrElse(index - 16) { getColor(TerminalColor.Normal.WHITE) }
        }
    }


    private fun getCubeColorValue(value: Int): Int {
        return if (value == 0) 0 else (40 * value + 55)
    }


    override fun getXTerm256Color(index: Int): Int {
        if (index <= 16) return getColor(index)
        return xterm256Colors.getOrElse(index - 17) { getColor(TerminalColor.Normal.WHITE) }
    }

}