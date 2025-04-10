package app.termora.terminal

import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class PtyProcessConnector(val process: PtyProcess, private val charset: Charset = StandardCharsets.UTF_8) :
    StreamPtyConnector(process.inputStream, process.outputStream) {

    private val reader = InputStreamReader(input)

    override fun read(buffer: CharArray): Int {
        return reader.read(buffer, 0, buffer.size)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        output.write(buffer, offset, len)
        output.flush()
    }


    override fun resize(rows: Int, cols: Int) {
        process.winSize = WinSize(cols, rows)
    }

    override fun waitFor(): Int {
        return process.waitFor()
    }

    override fun close() {
        // deadlock
        // IOUtils.closeQuietly(reader)
        process.destroyForcibly()
    }

    override fun getCharset(): Charset {
        return charset
    }
}