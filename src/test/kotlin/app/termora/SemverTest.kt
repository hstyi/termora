package app.termora

import org.semver4j.Semver
import kotlin.test.Test
import kotlin.test.assertTrue

class SemverTest {
    @Test
    fun test() {
        val a = Semver.parse("1.0.0") ?: throw IllegalArgumentException("Semver is invalid")
        assertTrue(a.satisfies("1.0.0 - 2.0.0"))
        assertTrue(a.satisfies(">=1.0.0"))
    }
}