package app.termora.plugin.internal.telnet

import app.termora.terminal.StreamPtyConnector
import org.apache.commons.net.telnet.TelnetClient
import org.apache.commons.net.telnet.TelnetOption
import org.apache.commons.net.telnet.WindowSizeOptionHandler
import java.io.InputStreamReader
import java.nio.charset.Charset

class TelnetStreamPtyConnector(
    private val telnet: TelnetClient,
    private val charset: Charset
) :
    StreamPtyConnector(telnet.inputStream, telnet.outputStream) {
    private val reader = InputStreamReader(telnet.inputStream, getCharset())

    override fun read(buffer: CharArray): Int {
        return reader.read(buffer)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        output.write(buffer, offset, len)
        output.flush()
    }

    override fun resize(rows: Int, cols: Int) {
        telnet.deleteOptionHandler(TelnetOption.WINDOW_SIZE)
        telnet.addOptionHandler(WindowSizeOptionHandler(cols, rows, true, false, true, false))
    }

    override fun waitFor(): Int {
        return -1
    }

    override fun close() {
        telnet.disconnect()
    }

    override fun getCharset(): Charset {
        return charset
    }
}