package app.termora

import app.termora.terminal.ColorTheme
import app.termora.terminal.TerminalColor
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.FlatPropertiesLaf
import com.formdev.flatlaf.util.SystemInfo
import java.util.*

class DraculaLaf : FlatPropertiesLaf("Dracula", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#282935",
            "@windowText" to "#eaeaea",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Basic.BACKGROUND -> 0x282935
            TerminalColor.Basic.FOREGROUND -> 0xeaeaea
            TerminalColor.Basic.SELECTION_BACKGROUND -> 0x56596b
            TerminalColor.Basic.SELECTION_FOREGROUND -> 0xfeffff
            TerminalColor.Basic.HYPERLINK -> 0x255ab4

            TerminalColor.Cursor.BACKGROUND -> 0xc7c7c7

            TerminalColor.Find.BACKGROUND -> 0xffff00
            TerminalColor.Find.FOREGROUND -> 0x282935

            TerminalColor.Normal.BLACK -> 0
            TerminalColor.Normal.RED -> 0xef766d
            TerminalColor.Normal.GREEN -> 0x88f397
            TerminalColor.Normal.YELLOW -> 0xf4f8a7
            TerminalColor.Normal.BLUE -> 0xc4a9f4
            TerminalColor.Normal.MAGENTA -> 0xf297cd
            TerminalColor.Normal.CYAN -> 0xaceafb
            TerminalColor.Normal.WHITE -> 0xc7c7c7

            TerminalColor.Bright.BLACK -> 0x676767
            TerminalColor.Bright.RED -> 0xef766d
            TerminalColor.Bright.GREEN -> 0x88f397
            TerminalColor.Bright.YELLOW -> 0xf4f8a7
            TerminalColor.Bright.BLUE -> 0xc4a9f4
            TerminalColor.Bright.MAGENTA -> 0xf297cd
            TerminalColor.Bright.CYAN -> 0xaceafb
            TerminalColor.Bright.WHITE -> 0xfeffff

            else -> Int.MAX_VALUE
        }
    }

}

class LightLaf : FlatLightLaf(), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0
            TerminalColor.Normal.RED -> 13501701
            TerminalColor.Normal.GREEN -> 425239
            TerminalColor.Normal.YELLOW -> 11701248
            TerminalColor.Normal.BLUE -> 409563
            TerminalColor.Normal.MAGENTA -> 11733427
            TerminalColor.Normal.CYAN -> 167566
            TerminalColor.Normal.WHITE -> 9605778

            TerminalColor.Bright.BLACK -> 0x4c4c4c
            TerminalColor.Bright.RED -> 0xff0000
            TerminalColor.Bright.GREEN -> 0x00ff00
            TerminalColor.Bright.YELLOW -> if (SystemInfo.isWindows) 0xC18301 else 0xffff00
            TerminalColor.Bright.BLUE -> 0x4682b4
            TerminalColor.Bright.MAGENTA -> 0xff00ff
            TerminalColor.Bright.CYAN -> 0x00ffff
            TerminalColor.Bright.WHITE -> 0xffffff

            else -> Int.MAX_VALUE
        }
    }
}


class DarkLaf : FlatDarkLaf(), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0
            TerminalColor.Normal.RED -> 15749711
            TerminalColor.Normal.GREEN -> 6067756
            TerminalColor.Normal.YELLOW -> 10914317
            TerminalColor.Normal.BLUE -> 3773396
            TerminalColor.Normal.MAGENTA -> 10973631
            TerminalColor.Normal.CYAN -> 41891
            TerminalColor.Normal.WHITE -> 8421504


            TerminalColor.Bright.BLACK -> 0x676767
            TerminalColor.Bright.RED -> 0xef766d
            TerminalColor.Bright.GREEN -> 0x8cf67a
            TerminalColor.Bright.YELLOW -> 0xfefb7e
            TerminalColor.Bright.BLUE -> 0x6a71f6
            TerminalColor.Bright.MAGENTA -> 0xf07ef8
            TerminalColor.Bright.CYAN -> 0x8ef9fd
            TerminalColor.Bright.WHITE -> 0xfeffff

//            TerminalColor.Basic.BACKGROUND -> 1974050

            else -> Int.MAX_VALUE
        }
    }
}

class iTerm2DarkLaf : FlatDarkLaf(), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {

            TerminalColor.Basic.BACKGROUND -> 0
            TerminalColor.Basic.FOREGROUND -> 0xc7c7c7
            TerminalColor.Basic.SELECTION_BACKGROUND -> 0xc6dcfc
            TerminalColor.Basic.SELECTION_FOREGROUND -> 0x000000
            TerminalColor.Basic.HYPERLINK -> 0x255ab4
            TerminalColor.Find.BACKGROUND -> 0xffff00
            TerminalColor.Find.FOREGROUND -> 0

            TerminalColor.Cursor.BACKGROUND -> 0xc7c7c7

            TerminalColor.Normal.BLACK -> 0
            TerminalColor.Normal.RED -> 0xb83019
            TerminalColor.Normal.GREEN -> 0x51bf37
            TerminalColor.Normal.YELLOW -> 0xc6c43d
            TerminalColor.Normal.BLUE -> 0x0c24bf
            TerminalColor.Normal.MAGENTA -> 0xb93ec1
            TerminalColor.Normal.CYAN -> 0x53c2c5
            TerminalColor.Normal.WHITE -> 0xc7c7c7


            TerminalColor.Bright.BLACK -> 0x676767
            TerminalColor.Bright.RED -> 0xef766d
            TerminalColor.Bright.GREEN -> 0x8cf67a
            TerminalColor.Bright.YELLOW -> 0xfefb7e
            TerminalColor.Bright.BLUE -> 0x6a71f6
            TerminalColor.Bright.MAGENTA -> 0xf07ef8
            TerminalColor.Bright.CYAN -> 0x8ef9fd
            TerminalColor.Bright.WHITE -> 0xfeffff


            else -> Int.MAX_VALUE
        }
    }
}


class TermiusLightLaf : FlatPropertiesLaf("Termius Light", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#d5dde0",
            "@windowText" to "#32364a",
        )
    )
}), ColorTheme {

    override fun getColor(color: TerminalColor): Int {

        return when (color) {
            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x32364a

            TerminalColor.Basic.FOREGROUND -> 0x32364a

            TerminalColor.Normal.BLACK -> 0x141729
            TerminalColor.Normal.RED -> 0xf24e50
            TerminalColor.Normal.GREEN -> 0x198c51
            TerminalColor.Normal.YELLOW -> 0xf8aa4b
            TerminalColor.Normal.BLUE -> 0x004878
            TerminalColor.Normal.MAGENTA -> 0x8f3c91
            TerminalColor.Normal.CYAN -> 0x2091f6
            TerminalColor.Normal.WHITE -> 0xeeeeee

            TerminalColor.Bright.BLACK -> 0x3e4257
            TerminalColor.Bright.RED -> 0xff7375
            TerminalColor.Bright.GREEN -> 0x21b568
            TerminalColor.Bright.YELLOW -> 0xfdc47d
            TerminalColor.Bright.BLUE -> 0x1d6da2
            TerminalColor.Bright.MAGENTA -> 0xff7dc5
            TerminalColor.Bright.CYAN -> 0x44a7ff
            TerminalColor.Bright.WHITE -> 0xffffff


            else -> Int.MAX_VALUE
        }
    }
}


class TermiusDarkLaf : FlatPropertiesLaf("Termius Dark", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#141729",
            "@windowText" to "#21b568",
        )
    )
}), ColorTheme {

    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x21b568

            TerminalColor.Basic.SELECTION_FOREGROUND -> 0

            TerminalColor.Basic.FOREGROUND -> 0x21b568

            TerminalColor.Normal.BLACK -> 0x343851
            TerminalColor.Normal.RED -> 0xf24e50
            TerminalColor.Normal.GREEN -> 0x008463
            TerminalColor.Normal.YELLOW -> 0xeca855
            TerminalColor.Normal.BLUE -> 0x08639f
            TerminalColor.Normal.MAGENTA -> 0xc13282
            TerminalColor.Normal.CYAN -> 0x2091f6
            TerminalColor.Normal.WHITE -> 0xe2e3e8

            TerminalColor.Bright.BLACK -> 0x8d91a5
            TerminalColor.Bright.RED -> 0xff7375
            TerminalColor.Bright.GREEN -> 0x3ed7be
            TerminalColor.Bright.YELLOW -> 0xfdc47d
            TerminalColor.Bright.BLUE -> 0x6ba0c3
            TerminalColor.Bright.MAGENTA -> 0xff7dc5
            TerminalColor.Bright.CYAN -> 0x44a7ff
            TerminalColor.Bright.WHITE -> 0xffffff

            else -> Int.MAX_VALUE
        }
    }
}

class NovelLaf : FlatPropertiesLaf("Novel", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#dfdbc3",
            "@windowText" to "#3b2322",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xd30f0f
            TerminalColor.Normal.GREEN -> 0x00933b
            TerminalColor.Normal.YELLOW -> 0xd38b40
            TerminalColor.Normal.BLUE -> 0x00528e
            TerminalColor.Normal.MAGENTA -> 0xcc32cf
            TerminalColor.Normal.CYAN -> 0x26c3e6
            TerminalColor.Normal.WHITE -> 0xa6a6a6

            TerminalColor.Bright.BLACK -> 0x5c5c5c
            TerminalColor.Bright.RED -> 0xe0692f
            TerminalColor.Bright.GREEN -> 0x00b400
            TerminalColor.Bright.YELLOW -> 0xfff284
            TerminalColor.Bright.BLUE -> 0x3ba6f3
            TerminalColor.Bright.MAGENTA -> 0xec88c2
            TerminalColor.Bright.CYAN -> 0x38daff
            TerminalColor.Bright.WHITE -> 0xf2f2f2

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x73635a


            else -> Int.MAX_VALUE
        }
    }
}


class AtomOneDarkLaf : FlatPropertiesLaf("Atom One Dark", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#1e2127",
            "@windowText" to "#abb2bf",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xca6169
            TerminalColor.Normal.GREEN -> 0x82a568
            TerminalColor.Normal.YELLOW -> 0xbf8c5d
            TerminalColor.Normal.BLUE -> 0x56a2e1
            TerminalColor.Normal.MAGENTA -> 0xb76ccd
            TerminalColor.Normal.CYAN -> 0x4e9aa3
            TerminalColor.Normal.WHITE -> 0xc5cbd6

            TerminalColor.Bright.BLACK -> 0x5c6370
            TerminalColor.Bright.RED -> 0xe77c84
            TerminalColor.Bright.GREEN -> 0xb4e294
            TerminalColor.Bright.YELLOW -> 0xe9b17b
            TerminalColor.Bright.BLUE -> 0x7ec5ff
            TerminalColor.Bright.MAGENTA -> 0xdb8df2
            TerminalColor.Bright.CYAN -> 0x64cfdd
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xabb2bf

            else -> Int.MAX_VALUE
        }
    }
}


class AtomOneLightLaf : FlatPropertiesLaf("Atom One Light", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#f9f9f9",
            "@windowText" to "#383a42",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xe45649
            TerminalColor.Normal.GREEN -> 0x4c9b4b
            TerminalColor.Normal.YELLOW -> 0xc99525
            TerminalColor.Normal.BLUE -> 0x4078f2
            TerminalColor.Normal.MAGENTA -> 0xa626a4
            TerminalColor.Normal.CYAN -> 0x0184bc
            TerminalColor.Normal.WHITE -> 0xb8b9bf

            TerminalColor.Bright.BLACK -> 0x474747
            TerminalColor.Bright.RED -> 0xff7468
            TerminalColor.Bright.GREEN -> 0x74ca72
            TerminalColor.Bright.YELLOW -> 0xdba633
            TerminalColor.Bright.BLUE -> 0x6a99ff
            TerminalColor.Bright.MAGENTA -> 0xc142bf
            TerminalColor.Bright.CYAN -> 0x00b1fd
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x383a42

            else -> Int.MAX_VALUE
        }
    }
}


class EverforestDarkLaf : FlatPropertiesLaf("Everforest Dark", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#282e32",
            "@windowText" to "#d3c6aa",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x42494e
            TerminalColor.Normal.RED -> 0xa1484a
            TerminalColor.Normal.GREEN -> 0x778e54
            TerminalColor.Normal.YELLOW -> 0xba9e68
            TerminalColor.Normal.BLUE -> 0x388084
            TerminalColor.Normal.MAGENTA -> 0x906378
            TerminalColor.Normal.CYAN -> 0x6ca37a
            TerminalColor.Normal.WHITE -> 0xc0dac6
            TerminalColor.Bright.BLACK -> 0x575656
            TerminalColor.Bright.RED -> 0xe67e80
            TerminalColor.Bright.GREEN -> 0xa7c080
            TerminalColor.Bright.YELLOW -> 0xdbbc7f
            TerminalColor.Bright.BLUE -> 0x7fbbb3
            TerminalColor.Bright.MAGENTA -> 0xd699b6
            TerminalColor.Bright.CYAN -> 0x83c092
            TerminalColor.Bright.WHITE -> 0xe8f4eb

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xd3c6aa

            else -> Int.MAX_VALUE
        }
    }
}


class EverforestLightLaf : FlatPropertiesLaf("Everforest Light", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#fefbf1",
            "@windowText" to "#5c6a72",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x42494e
            TerminalColor.Normal.RED -> 0xd2413e
            TerminalColor.Normal.GREEN -> 0x919d45
            TerminalColor.Normal.YELLOW -> 0xd89902
            TerminalColor.Normal.BLUE -> 0x2b7ba7
            TerminalColor.Normal.MAGENTA -> 0xbc72a5
            TerminalColor.Normal.CYAN -> 0x50b08c
            TerminalColor.Normal.WHITE -> 0xc8d0c9
            TerminalColor.Bright.BLACK -> 0x575656
            TerminalColor.Bright.RED -> 0xe67e80
            TerminalColor.Bright.GREEN -> 0xa7c080
            TerminalColor.Bright.YELLOW -> 0xdbbc7f
            TerminalColor.Bright.BLUE -> 0x7fbbb3
            TerminalColor.Bright.MAGENTA -> 0xd699b6
            TerminalColor.Bright.CYAN -> 0x83c092
            TerminalColor.Bright.WHITE -> 0xd7e2d8

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x5c6a72

            else -> Int.MAX_VALUE
        }
    }
}


class NightOwlLaf : FlatPropertiesLaf("Night Owl", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#011627",
            "@windowText" to "#d6deeb",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x072945
            TerminalColor.Normal.RED -> 0xef5350
            TerminalColor.Normal.GREEN -> 0x22da6e
            TerminalColor.Normal.YELLOW -> 0xc5e478
            TerminalColor.Normal.BLUE -> 0x82aaff
            TerminalColor.Normal.MAGENTA -> 0xc792ea
            TerminalColor.Normal.CYAN -> 0x21c7a8
            TerminalColor.Normal.WHITE -> 0xe1f1ff
            TerminalColor.Bright.BLACK -> 0x575656
            TerminalColor.Bright.RED -> 0xff7472
            TerminalColor.Bright.GREEN -> 0x40fa8d
            TerminalColor.Bright.YELLOW -> 0xffeb95
            TerminalColor.Bright.BLUE -> 0xa0beff
            TerminalColor.Bright.MAGENTA -> 0xdaa4ff
            TerminalColor.Bright.CYAN -> 0x7fdbca
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x80a4c2

            else -> Int.MAX_VALUE
        }
    }
}


class LightOwlLaf : FlatPropertiesLaf("Light Owl", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#fbfbfb",
            "@windowText" to "#403f53",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x403f53
            TerminalColor.Normal.RED -> 0xde3d3b
            TerminalColor.Normal.GREEN -> 0x08916a
            TerminalColor.Normal.YELLOW -> 0xe0af02
            TerminalColor.Normal.BLUE -> 0x288ed7
            TerminalColor.Normal.MAGENTA -> 0xd6438a
            TerminalColor.Normal.CYAN -> 0x2aa298
            TerminalColor.Normal.WHITE -> 0xe8e5e5
            TerminalColor.Bright.BLACK -> 0x57566d
            TerminalColor.Bright.RED -> 0xfa5d5b
            TerminalColor.Bright.GREEN -> 0x1abf90
            TerminalColor.Bright.YELLOW -> 0xf4c315
            TerminalColor.Bright.BLUE -> 0x3ca3ec
            TerminalColor.Bright.MAGENTA -> 0xf559a4
            TerminalColor.Bright.CYAN -> 0x39c6ba
            TerminalColor.Bright.WHITE -> 0xf6f6f6

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x90a7b2

            else -> Int.MAX_VALUE
        }
    }
}


class AuraLaf : FlatPropertiesLaf("Aura", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#21202e",
            "@windowText" to "#edecee",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x1c1b22
            TerminalColor.Normal.RED -> 0xff6767
            TerminalColor.Normal.GREEN -> 0x4deeb8
            TerminalColor.Normal.YELLOW -> 0xf4be77
            TerminalColor.Normal.BLUE -> 0x5b72ee
            TerminalColor.Normal.MAGENTA -> 0xa277ff
            TerminalColor.Normal.CYAN -> 0x51fafa
            TerminalColor.Normal.WHITE -> 0xdddbfa
            TerminalColor.Bright.BLACK -> 0x4d4d4d
            TerminalColor.Bright.RED -> 0xffa285
            TerminalColor.Bright.GREEN -> 0x99ffdd
            TerminalColor.Bright.YELLOW -> 0xffd49d
            TerminalColor.Bright.BLUE -> 0x8296ff
            TerminalColor.Bright.MAGENTA -> 0xb592ff
            TerminalColor.Bright.CYAN -> 0x8cffff
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xedecee

            else -> Int.MAX_VALUE
        }
    }
}


class Cobalt2Laf : FlatPropertiesLaf("Cobalt2", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#132738",
            "@windowText" to "#ffffff",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xff0000
            TerminalColor.Normal.GREEN -> 0x38de21
            TerminalColor.Normal.YELLOW -> 0xffe50a
            TerminalColor.Normal.BLUE -> 0x1460d2
            TerminalColor.Normal.MAGENTA -> 0xff4387
            TerminalColor.Normal.CYAN -> 0x00bbbb
            TerminalColor.Normal.WHITE -> 0xcfcfcf
            TerminalColor.Bright.BLACK -> 0x555555
            TerminalColor.Bright.RED -> 0xff757a
            TerminalColor.Bright.GREEN -> 0x69fb79
            TerminalColor.Bright.YELLOW -> 0xfff285
            TerminalColor.Bright.BLUE -> 0x77adff
            TerminalColor.Bright.MAGENTA -> 0xff92cc
            TerminalColor.Bright.CYAN -> 0x6bffff
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xf0cc09

            else -> Int.MAX_VALUE
        }
    }
}


class OctocatDarkLaf : FlatPropertiesLaf("Octocat Dark", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#101216",
            "@windowText" to "#8b949e",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xf78166
            TerminalColor.Normal.GREEN -> 0x56d364
            TerminalColor.Normal.YELLOW -> 0xe3b341
            TerminalColor.Normal.BLUE -> 0x6ca4f8
            TerminalColor.Normal.MAGENTA -> 0xdb61a2
            TerminalColor.Normal.CYAN -> 0x2b7489
            TerminalColor.Normal.WHITE -> 0xDADADA
            TerminalColor.Bright.BLACK -> 0x4d4d4d
            TerminalColor.Bright.RED -> 0xffb5a5
            TerminalColor.Bright.GREEN -> 0x69fb79
            TerminalColor.Bright.YELLOW -> 0xffcf5f
            TerminalColor.Bright.BLUE -> 0xb0d0ff
            TerminalColor.Bright.MAGENTA -> 0xff92cc
            TerminalColor.Bright.CYAN -> 0x54d8ff
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xc9d1d9

            else -> Int.MAX_VALUE
        }
    }
}


class OctocatLightLaf : FlatPropertiesLaf("Octocat Light", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#f4f4f4",
            "@windowText" to "#3e3e3e",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xff0000
            TerminalColor.Normal.GREEN -> 0x38de21
            TerminalColor.Normal.YELLOW -> 0xffe50a
            TerminalColor.Normal.BLUE -> 0x1460d2
            TerminalColor.Normal.MAGENTA -> 0xff4387
            TerminalColor.Normal.CYAN -> 0x00bbbb
            TerminalColor.Normal.WHITE -> 0xcfcfcf
            TerminalColor.Bright.BLACK -> 0x555555
            TerminalColor.Bright.RED -> 0xff757a
            TerminalColor.Bright.GREEN -> 0x69fb79
            TerminalColor.Bright.YELLOW -> 0xfff285
            TerminalColor.Bright.BLUE -> 0x77adff
            TerminalColor.Bright.MAGENTA -> 0xff92cc
            TerminalColor.Bright.CYAN -> 0x6bffff
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x3f3f3f

            else -> Int.MAX_VALUE
        }
    }
}


class AyuDarkLaf : FlatPropertiesLaf("Ayu Dark", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#0f1419",
            "@windowText" to "#e6e1cf",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xff3333
            TerminalColor.Normal.GREEN -> 0xb8cc52
            TerminalColor.Normal.YELLOW -> 0xdbb012
            TerminalColor.Normal.BLUE -> 0x36a3d9
            TerminalColor.Normal.MAGENTA -> 0xdf7a80
            TerminalColor.Normal.CYAN -> 0x6ceedf
            TerminalColor.Normal.WHITE -> 0xababab
            TerminalColor.Bright.BLACK -> 0x323232
            TerminalColor.Bright.RED -> 0xff8181
            TerminalColor.Bright.GREEN -> 0xeafe84
            TerminalColor.Bright.YELLOW -> 0xffe174
            TerminalColor.Bright.BLUE -> 0x68d5ff
            TerminalColor.Bright.MAGENTA -> 0xffa3aa
            TerminalColor.Bright.CYAN -> 0x94fff1
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xf29718

            else -> Int.MAX_VALUE
        }
    }
}


class AyuLightLaf : FlatPropertiesLaf("Ayu Light", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#fafafa",
            "@windowText" to "#5c6773",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xff3333
            TerminalColor.Normal.GREEN -> 0x319900
            TerminalColor.Normal.YELLOW -> 0xf29718
            TerminalColor.Normal.BLUE -> 0x41a6d9
            TerminalColor.Normal.MAGENTA -> 0xe07ead
            TerminalColor.Normal.CYAN -> 0x1dd1b0
            TerminalColor.Normal.WHITE -> 0xdfdddd
            TerminalColor.Bright.BLACK -> 0x323232
            TerminalColor.Bright.RED -> 0xff5959
            TerminalColor.Bright.GREEN -> 0xb8e532
            TerminalColor.Bright.YELLOW -> 0xffc94a
            TerminalColor.Bright.BLUE -> 0x73d8ff
            TerminalColor.Bright.MAGENTA -> 0xffa3aa
            TerminalColor.Bright.CYAN -> 0x7ff1cb
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xff6a00

            else -> Int.MAX_VALUE
        }
    }
}


class HomebrewLaf : FlatPropertiesLaf("Homebrew", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#000000",
            "@windowText" to "#00ff00",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x2e2e2e
            TerminalColor.Normal.RED -> 0xc93434
            TerminalColor.Normal.GREEN -> 0x348e48
            TerminalColor.Normal.YELLOW -> 0xe09e00
            TerminalColor.Normal.BLUE -> 0x0031e0
            TerminalColor.Normal.MAGENTA -> 0xe235ff
            TerminalColor.Normal.CYAN -> 0x3fc1dd
            TerminalColor.Normal.WHITE -> 0xd0cfcf
            TerminalColor.Bright.BLACK -> 0x5b5b5b
            TerminalColor.Bright.RED -> 0xff6767
            TerminalColor.Bright.GREEN -> 0x31ff31
            TerminalColor.Bright.YELLOW -> 0xffdca8
            TerminalColor.Bright.BLUE -> 0x4465da
            TerminalColor.Bright.MAGENTA -> 0xff5fc8
            TerminalColor.Bright.CYAN -> 0x8debff
            TerminalColor.Bright.WHITE -> 0xe6e6e6

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x23ff18

            TerminalColor.Basic.FOREGROUND -> 0x00ff00

            else -> Int.MAX_VALUE
        }
    }
}


class ProLaf : FlatPropertiesLaf("Pro", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#000000",
            "@windowText" to "#f2f2f2",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x2e2e2e
            TerminalColor.Normal.RED -> 0xc93434
            TerminalColor.Normal.GREEN -> 0x348e48
            TerminalColor.Normal.YELLOW -> 0xe09e00
            TerminalColor.Normal.BLUE -> 0x002bc7
            TerminalColor.Normal.MAGENTA -> 0xe235ff
            TerminalColor.Normal.CYAN -> 0x3fc1dd
            TerminalColor.Normal.WHITE -> 0xd0cfcf
            TerminalColor.Bright.BLACK -> 0x5b5b5b
            TerminalColor.Bright.RED -> 0xff6767
            TerminalColor.Bright.GREEN -> 0x31ff31
            TerminalColor.Bright.YELLOW -> 0xffdca8
            TerminalColor.Bright.BLUE -> 0x4465da
            TerminalColor.Bright.MAGENTA -> 0xff5fc8
            TerminalColor.Bright.CYAN -> 0x8debff
            TerminalColor.Bright.WHITE -> 0xe6e6e6

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x4d4d4d

            TerminalColor.Basic.FOREGROUND -> 0xf2f2f2

            else -> Int.MAX_VALUE
        }
    }
}


class NordLightLaf : FlatPropertiesLaf("Nord Light", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#e5e9f0",
            "@windowText" to "#414858",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x2c3344
            TerminalColor.Normal.RED -> 0xae545d
            TerminalColor.Normal.GREEN -> 0x8ca377
            TerminalColor.Normal.YELLOW -> 0xdabe84
            TerminalColor.Normal.BLUE -> 0x718fae
            TerminalColor.Normal.MAGENTA -> 0x95728e
            TerminalColor.Normal.CYAN -> 0x78acbb
            TerminalColor.Normal.WHITE -> 0xd8dee9
            TerminalColor.Bright.BLACK -> 0x4c556a
            TerminalColor.Bright.RED -> 0xd97982
            TerminalColor.Bright.GREEN -> 0xa3be8b
            TerminalColor.Bright.YELLOW -> 0xeacb8a
            TerminalColor.Bright.BLUE -> 0xa4c7e9
            TerminalColor.Bright.MAGENTA -> 0xb48dac
            TerminalColor.Bright.CYAN -> 0x8fbcbb
            TerminalColor.Bright.WHITE -> 0xeceff4

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x88c0d0

            TerminalColor.Basic.FOREGROUND -> 0x414858

            else -> Int.MAX_VALUE
        }
    }
}


class NordDarkLaf : FlatPropertiesLaf("Nord Dark", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#2e3440",
            "@windowText" to "#d8dee9",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x3b4252
            TerminalColor.Normal.RED -> 0xae545d
            TerminalColor.Normal.GREEN -> 0x8ca377
            TerminalColor.Normal.YELLOW -> 0xdabe84
            TerminalColor.Normal.BLUE -> 0x718fae
            TerminalColor.Normal.MAGENTA -> 0x95728e
            TerminalColor.Normal.CYAN -> 0x78acbb
            TerminalColor.Normal.WHITE -> 0xd8dee9
            TerminalColor.Bright.BLACK -> 0x4c556a
            TerminalColor.Bright.RED -> 0xd97982
            TerminalColor.Bright.GREEN -> 0xa3be8b
            TerminalColor.Bright.YELLOW -> 0xeacb8a
            TerminalColor.Bright.BLUE -> 0xa4c7e9
            TerminalColor.Bright.MAGENTA -> 0xb48dac
            TerminalColor.Bright.CYAN -> 0x8fbcbb
            TerminalColor.Bright.WHITE -> 0xeceff4

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xeceff4

            TerminalColor.Basic.FOREGROUND -> 0xd8dee9


            else -> Int.MAX_VALUE
        }
    }
}


class GitHubLightLaf : FlatPropertiesLaf("GitHub Light", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "light",
            "@background" to "#f4f4f4",
            "@windowText" to "#3e3e3e",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x3e3e3e
            TerminalColor.Normal.RED -> 0x970b16
            TerminalColor.Normal.GREEN -> 0x07962a
            TerminalColor.Normal.YELLOW -> 0xf8eec7
            TerminalColor.Normal.BLUE -> 0x003e8a
            TerminalColor.Normal.MAGENTA -> 0xe94691
            TerminalColor.Normal.CYAN -> 0x89d1ec
            TerminalColor.Normal.WHITE -> 0x3e3e3e
            TerminalColor.Bright.BLACK -> 0x666666
            TerminalColor.Bright.RED -> 0xde0000
            TerminalColor.Bright.GREEN -> 0x87d5a2
            TerminalColor.Bright.YELLOW -> 0xf1d007
            TerminalColor.Bright.BLUE -> 0x2e6cba
            TerminalColor.Bright.MAGENTA -> 0xffa29f
            TerminalColor.Bright.CYAN -> 0x1cfafe
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x3f3f3f

            TerminalColor.Basic.FOREGROUND -> 0x3e3e3e

            else -> Int.MAX_VALUE
        }
    }
}


class GitHubDarkLaf : FlatPropertiesLaf("GitHub Dark", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#101216",
            "@windowText" to "#8b949e",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x000000
            TerminalColor.Normal.RED -> 0xf78166
            TerminalColor.Normal.GREEN -> 0x56d364
            TerminalColor.Normal.YELLOW -> 0xe3b341
            TerminalColor.Normal.BLUE -> 0x6ca4f8
            TerminalColor.Normal.MAGENTA -> 0xdb61a2
            TerminalColor.Normal.CYAN -> 0x2b7489
            TerminalColor.Normal.WHITE -> 0x8b949e
            TerminalColor.Bright.BLACK -> 0x4d4d4d
            TerminalColor.Bright.RED -> 0xf78166
            TerminalColor.Bright.GREEN -> 0x56d364
            TerminalColor.Bright.YELLOW -> 0xe3b341
            TerminalColor.Bright.BLUE -> 0x6ca4f8
            TerminalColor.Bright.MAGENTA -> 0xdb61a2
            TerminalColor.Bright.CYAN -> 0x2b7489
            TerminalColor.Bright.WHITE -> 0xffffff

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0xc9d1d9

            TerminalColor.Basic.FOREGROUND -> 0x8b949e


            else -> Int.MAX_VALUE
        }
    }
}


class ChalkLaf : FlatPropertiesLaf("Chalk", Properties().apply {
    putAll(
        mapOf(
            "@baseTheme" to "dark",
            "@background" to "#2b2d2e",
            "@windowText" to "#d2d8d9",
        )
    )
}), ColorTheme {
    override fun getColor(color: TerminalColor): Int {
        return when (color) {
            TerminalColor.Normal.BLACK -> 0x7d8b8f
            TerminalColor.Normal.RED -> 0xb23a52
            TerminalColor.Normal.GREEN -> 0x789b6a
            TerminalColor.Normal.YELLOW -> 0xb9ac4a
            TerminalColor.Normal.BLUE -> 0x2a7fac
            TerminalColor.Normal.MAGENTA -> 0xbd4f5a
            TerminalColor.Normal.CYAN -> 0x44a799
            TerminalColor.Normal.WHITE -> 0xd2d8d9
            TerminalColor.Bright.BLACK -> 0x888888
            TerminalColor.Bright.RED -> 0xf24840
            TerminalColor.Bright.GREEN -> 0x80c470
            TerminalColor.Bright.YELLOW -> 0xffeb62
            TerminalColor.Bright.BLUE -> 0x4196ff
            TerminalColor.Bright.MAGENTA -> 0xfc5275
            TerminalColor.Bright.CYAN -> 0x53cdbd
            TerminalColor.Bright.WHITE -> 0xd2d8d9

            TerminalColor.Basic.SELECTION_BACKGROUND,
            TerminalColor.Cursor.BACKGROUND -> 0x708284

            TerminalColor.Basic.FOREGROUND -> 0xd2d8d9


            else -> Int.MAX_VALUE
        }
    }
}
