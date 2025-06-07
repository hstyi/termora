package app.termora

import com.formdev.flatlaf.FlatLaf
import org.apache.commons.codec.digest.MurmurHash3
import java.awt.Color

object ColorHash {
    fun hash(text: String): Color {
        val hash = MurmurHash3.hash32x86(text.toByteArray())

        val r = (hash shr 16) and 0xFF
        val g = (hash shr 8) and 0xFF
        val b = hash and 0xFF

        val color = Color(r, g, b)

        if (FlatLaf.isLafDark()) {
            return color.brighter()
        }

        return color.darker()
    }
}