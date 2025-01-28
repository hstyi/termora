package app.termora

import app.termora.terminal.StreamPtyConnector
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import java.io.InputStreamReader
import java.nio.charset.Charset

class ChannelShellPtyConnector(
    private val channel: ChannelShell,
    private val charset: Charset = Charsets.UTF_8
) : StreamPtyConnector(channel.invertedOut, channel.invertedIn) {

    private val reader = InputStreamReader(input, charset)

    override fun read(buffer: CharArray): Int {
        return reader.read(buffer)
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        output.write(buffer, offset, len)
        output.flush()
    }

    override fun resize(rows: Int, cols: Int) {
        channel.sendWindowChange(cols, rows)
    }

    override fun waitFor(): Int {
        channel.waitFor(listOf(ClientChannelEvent.CLOSED), Long.MAX_VALUE)
        return channel.exitStatus
    }

    override fun close() {
        channel.close(true)
    }

    override fun getCharset(): Charset {
        return charset
    }
}