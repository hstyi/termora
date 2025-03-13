package app.termora.terminal.panel

import java.nio.ByteBuffer
import java.nio.charset.Charset
import javax.swing.JComponent

interface TerminalWriter {

    /**
     * 挂载
     */
    fun onMounted(c: JComponent)

    /**
     * 将数据写入
     */
    fun write(request: WriteRequest)

    /**
     * 重置大小
     */
    fun resize(rows: Int, cols: Int)

    /**
     * 字符集
     */
    fun getCharset(): Charset = Charsets.UTF_8

    class WriteRequest private constructor(val buffer: ByteArray) {

        companion object {
            fun fromBytes(bytes: ByteArray): WriteRequest {
                return WriteRequest(bytes)
            }

            fun fromBytes(buffer: ByteArray, offset: Int, len: Int): WriteRequest {
                return WriteRequest(buffer.copyOfRange(offset, offset + len))
            }

            fun fromInt(buffer: Int): WriteRequest {
                return fromBytes(ByteBuffer.allocate(Integer.BYTES).putInt(buffer).flip().array())
            }

        }
    }
}