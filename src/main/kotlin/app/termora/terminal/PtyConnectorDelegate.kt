package app.termora.terminal

import java.nio.charset.Charset

open class PtyConnectorDelegate(
    @Volatile var ptyConnector: PtyConnector? = null
) : PtyConnector {


    override fun read(buffer: CharArray): Int {
        return (ptyConnector ?: return 0).read(buffer)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        ptyConnector?.write(buffer, offset, len)
    }

    override fun resize(rows: Int, cols: Int) {
        ptyConnector?.resize(rows, cols)
    }

    override fun waitFor(): Int {
        return ptyConnector?.waitFor() ?: 0
    }

    override fun close() {
        ptyConnector?.close()
        ptyConnector = null
    }

    override fun getCharset(): Charset {
        return ptyConnector?.getCharset() ?: super.getCharset()
    }
}