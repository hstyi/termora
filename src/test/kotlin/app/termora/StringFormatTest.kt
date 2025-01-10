package app.termora

import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

class StringFormatTest {
    @Test
    fun test() {
        assertFailsWith(IllegalFormatConversionException::class) { String.format("%.0f%%", 99) }
        assertDoesNotThrow { String.format("%.0f%%", 99.0) }
    }
}