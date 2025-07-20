package app.termora.plugins.vnc

import com.glavsoft.rfb.IRfbSessionListener
import com.glavsoft.rfb.encoding.EncodingType
import com.glavsoft.rfb.protocol.Protocol
import com.glavsoft.rfb.protocol.ProtocolSettings
import com.glavsoft.transport.BaudrateMeter
import com.glavsoft.transport.Transport
import com.glavsoft.viewer.swing.ClipboardControllerImpl
import java.awt.BorderLayout
import java.awt.Dimension
import java.net.InetSocketAddress
import java.net.Socket
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities


class RfbClientTest {

    //    @Test
    fun myTest() {

        SwingUtilities.invokeLater {
            val socket = Socket()
            socket.connect(InetSocketAddress("10.211.55.14", 5900))
            socket.tcpNoDelay = true

            val transport = Transport(socket)
            transport.setBaudrateMeter(BaudrateMeter())
            val protocolSettings = ProtocolSettings.getDefaultSettings()
            protocolSettings.preferredEncoding = EncodingType.ZRLE
            val protocol = Protocol(transport, { "123456" }, protocolSettings)
            protocol.handshake()
            val surface = MySurface(protocol)

            try {
                protocol.startNormalHandling(object : IRfbSessionListener {
                    override fun rfbSessionStopped(p0: String?) {

                    }

                }, surface, ClipboardControllerImpl(protocol, "GBK"))
            } catch (e: Exception) {
                e.printStackTrace()
            }


            val frame = JFrame()
            val panel = JPanel(BorderLayout())
            panel.add(JTextField(), BorderLayout.NORTH)
            panel.add(surface, BorderLayout.CENTER)
            frame.contentPane.add(panel)
            frame.size = Dimension(1024, 800)
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
        }
        Thread.currentThread().join()
    }

}