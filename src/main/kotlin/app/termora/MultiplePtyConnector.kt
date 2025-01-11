package app.termora


import app.termora.terminal.PtyConnector
import app.termora.terminal.PtyConnectorDelegate
import org.jdesktop.swingx.action.ActionManager

/**
 * 当开启转发时，会获取到所有的 [PtyConnector] 然后跳过中间层，直接找到最近的一个 [MultiplePtyConnector]，如果找不到那就以最后一个匹配不到的为准 [getMultiplePtyConnector]。
 */
class MultiplePtyConnector(
    private val scope: Scope,
    private val myConnector: PtyConnector
) : PtyConnectorDelegate(myConnector) {

    private val isMultiple get() = ActionManager.getInstance().isSelected(Actions.MULTIPLE)
    private val ptyConnectors get() = PtyConnectorFactory.getInstance(scope).getPtyConnectors()

    override fun write(buffer: ByteArray, offset: Int, len: Int) {
        if (isMultiple) {
            for (connector in ptyConnectors) {
                getMultiplePtyConnector(connector).write(buffer, offset, len)
            }
        } else {
            myConnector.write(buffer, offset, len)
        }
    }


    private fun getMultiplePtyConnector(connector: PtyConnector): PtyConnector {
        if (connector is MultiplePtyConnector) {
            val c = connector.myConnector
            if (c is MultiplePtyConnector) {
                return getMultiplePtyConnector(c)
            }
            return c
        }

        if (connector is PtyConnectorDelegate) {
            val c = connector.ptyConnector
            if (c != null) {
                return getMultiplePtyConnector(c)
            }
        }

        return connector
    }

}