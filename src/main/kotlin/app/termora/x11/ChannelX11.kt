package app.termora.x11

import org.apache.sshd.client.channel.AbstractClientChannel
import org.apache.sshd.client.future.DefaultOpenFuture
import org.apache.sshd.client.future.OpenFuture
import org.apache.sshd.client.session.ClientConnectionService
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.SshConstants
import org.apache.sshd.common.channel.ChannelOutputStream
import org.apache.sshd.common.io.IoConnectFuture
import org.apache.sshd.common.io.IoSession
import org.apache.sshd.common.util.buffer.Buffer
import org.apache.sshd.common.util.buffer.ByteArrayBuffer
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ChannelX11(
    private val host: String,
    private val port: Int,
) : AbstractClientChannel("x11") {

    companion object {
        val X11_COOKIE = AttributeRepository.AttributeKey<ByteArray>()
        val X11_COOKIE_HEX = AttributeRepository.AttributeKey<String>()
    }

    private lateinit var x11: IoSession
    private val isInitialized = AtomicBoolean(false)

    override fun open(recipient: Long, rwSize: Long, packetSize: Long, buffer: Buffer): OpenFuture {
        val openFuture = DefaultOpenFuture(this, futureLock).apply { openFuture = this }

        connectX11Server().addListener {
            if (it.isConnected) {
                this.x11 = it.session
                handleOpenSuccess(recipient, rwSize, packetSize, buffer)
            } else {
                if (it.exception != null) {
                    openFuture.exception = it.exception
                } else {
                    openFuture.value = false
                }
                unregisterSelf()
            }
        }

        return openFuture
    }

    override fun doOpen() {
        this.out = ChannelOutputStream(
            this, remoteWindow, log,
            SshConstants.SSH_MSG_CHANNEL_DATA, true
        )
    }

    private fun connectX11Server(): IoConnectFuture {
        val connector = session.factoryManager.ioServiceFactory.createConnector(X11IoHandler(this))
        val future = connector.connect(InetSocketAddress(host, port), session, null)
        addCloseFutureListener { if (it.isClosed) connector.close(true) }
        return future
    }


    override fun doWriteData(data: ByteArray, off: Int, len: Long) {
        if (isInitialized.compareAndSet(false, true)) {
            val cookie = session.getAttribute(X11_COOKIE) ?: return
            val foo = data.copyOfRange(off, off + len.toInt())
            val s = 0
            val l = foo.size
            if (l < 9) return

            var plen = (foo[s + 6].toInt() and 0xff) * 256 + (foo[s + 7].toInt() and 0xff)
            var dlen = (foo[s + 8].toInt() and 0xff) * 256 + (foo[s + 9].toInt() and 0xff)
            if ((foo[s].toInt() and 0xff) == 0x6c) {
                plen = ((plen ushr 8) and 0xff) or ((plen shl 8) and 0xff00)
                dlen = ((dlen ushr 8) and 0xff) or ((dlen shl 8) and 0xff00)
            }

            if (l < 12 + plen + ((-plen) and 3) + dlen) return

            val bar = ByteArray(dlen)
            System.arraycopy(foo, s + 12 + plen + ((-plen) and 3), bar, 0, dlen)

            if (Objects.deepEquals(cookie, bar) && x11.isOpen) {
                x11.writeBuffer(ByteArrayBuffer(foo, s, l))
            } else {
                sendEof()
            }
        } else if (x11.isOpen) {
            x11.writeBuffer(ByteArrayBuffer(data, off, len.toInt()))
        }
    }

    override fun handleEof() {
        super.handleEof()
        close(true)
    }

    private fun unregisterSelf() {
        try {
            session.getService(ClientConnectionService::class.java)
                .unregisterChannel(this)
            close(true)
        } catch (e: Exception) {
            if (log.isWarnEnabled) {
                log.error(e.message, e)
            }
        }
    }

}