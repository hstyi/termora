package app.termora

import app.termora.terminal.PtyConnector
import org.apache.commons.io.Charsets
import java.nio.charset.StandardCharsets

class SerialTerminalTab(windowScope: WindowScope, host: Host) : PtyHostTerminalTab(windowScope, host) {
    override suspend fun openPtyConnector(): PtyConnector {
        val serialPort = Serials.openPort(host)
        return SerialPortPtyConnector(
            serialPort,
            Charsets.toCharset(host.options.encoding, StandardCharsets.UTF_8)
        )
    }
}