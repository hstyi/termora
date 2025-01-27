package app.termora

import app.termora.terminal.PtyConnector

class SerialTerminalTab(windowScope: WindowScope, host: Host) : PtyHostTerminalTab(windowScope, host) {
    override suspend fun openPtyConnector(): PtyConnector {
        val serialPort = Serials.openPort(host)
        return SerialPortPtyConnector(serialPort)
    }
}