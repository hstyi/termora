package app.termora.macro

import app.termora.Actions

import app.termora.terminal.PtyConnector
import app.termora.terminal.PtyConnectorDelegate
import org.jdesktop.swingx.action.ActionManager
import java.util.*

class MacroPtyConnector(private val connector: PtyConnector) : PtyConnectorDelegate(connector) {
    private val isRecording get() = ActionManager.getInstance().isSelected(Actions.MACRO)

    companion object {
        private val bytes = LinkedList<Byte>()

        fun getRecodingByteArray(): ByteArray {
            val array = bytes.toByteArray()
            bytes.clear()
            return array
        }
    }

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        if (isRecording) {
            for (i in offset until len) {
                bytes.add(buffer[i])
            }
        }
        connector.write(buffer, offset, len)
    }


}