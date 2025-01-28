package app.termora.terminal

import java.nio.ByteBuffer
import java.nio.charset.Charset


interface PtyConnector {

    /**
     * 读取
     */
    fun read(buffer: CharArray): Int

    /**
     * 将数据写入
     */
    fun write(buffer: ByteArray, offset: Int, len: Int)

    /**
     * 写入数组。
     *
     * 如果要写入 String 字符串，请通过 [getCharset] 编码。
     */
    fun write(buffer: ByteArray) {
        write(buffer, 0, buffer.size)
    }

    /**
     * 写入单个 Int
     */
    fun write(buffer: Int) {
        write(ByteBuffer.allocate(Integer.BYTES).putInt(buffer).flip().array())
    }

    /**
     * 修改 pty 大小
     */
    fun resize(rows: Int, cols: Int)

    /**
     * 等待断开
     */
    fun waitFor(): Int

    /**
     * 关闭
     */
    fun close()

    /**
     * 编码
     */
    fun getCharset(): Charset = Charsets.UTF_8
}