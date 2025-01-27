package app.termora

import app.termora.terminal.PtyConnector
import com.fazecast.jSerialComm.SerialPort
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class SerialPortPtyConnector(
    private val serialPort: SerialPort,
    private val charset: Charset = Charsets.UTF_8
) : PtyConnector {
    private val reader = InputStreamReader(object : InputStream() {
        override fun read(): Int {
            val buffer = ByteArray(1)
            return when (val len = serialPort.readBytes(buffer, buffer.size)) {
                0 -> 0
                1 -> buffer[0].toInt()
                else -> len
            }
        }

    })

    override fun read(buffer: CharArray): Int {
        return reader.read(buffer, 0, buffer.size)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        serialPort.writeBytes(buffer, offset, len)
    }

    override fun resize(rows: Int, cols: Int) {

    }

    override fun waitFor(): Int {
        return 0
    }

    override fun close() {
        serialPort.closePort()
    }
}