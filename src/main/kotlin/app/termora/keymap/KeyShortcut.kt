package app.termora.keymap

import org.apache.commons.lang3.StringUtils
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class KeyShortcut(val keyStroke: KeyStroke) : Shortcut() {

    companion object {
        fun toHumanText(keyStroke: KeyStroke): String {

            var text = keyStroke.toString()
            text = text.replace("shift", "⇧")
            text = text.replace("ctrl", "⌃")
            text = text.replace("meta", "⌘")
            text = text.replace("alt", "⌥")
            text = text.replace("pressed", StringUtils.EMPTY)
            text = text.replace(StringUtils.SPACE, StringUtils.EMPTY)

            if (keyStroke.keyCode == KeyEvent.VK_EQUALS) {
                text = text.replace("EQUALS", "+")
            } else if (keyStroke.keyCode == KeyEvent.VK_MINUS) {
                text = text.replace("MINUS", "-")
            }

            return text.toCharArray().joinToString(" + ")
        }
    }

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

    override fun toString(): String {
        return toHumanText(keyStroke)
    }
}