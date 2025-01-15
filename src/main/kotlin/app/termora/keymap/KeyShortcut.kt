package app.termora.keymap

import javax.swing.KeyStroke

class KeyShortcut(val keyStroke: KeyStroke) : Shortcut() {
    override fun isKeyboard(): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyShortcut

        return keyStroke == other.keyStroke
    }

    override fun hashCode(): Int {
        return keyStroke.hashCode()
    }
}