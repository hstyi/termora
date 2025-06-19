package app.termora.vfs

import okio.Path.Companion.toPath
import kotlin.test.Test

class TestFileSystem {
    @Test
    fun test() {
        println(okio.FileSystem.SYSTEM.list(".".toPath()))
    }
}