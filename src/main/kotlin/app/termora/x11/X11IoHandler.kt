package app.termora.x11

import org.apache.sshd.common.io.IoSession
import org.apache.sshd.common.session.helpers.AbstractSession
import org.apache.sshd.common.session.helpers.AbstractSessionIoHandler
import org.apache.sshd.common.util.Readable
import org.apache.sshd.common.util.io.IoUtils
import kotlin.math.min

class X11IoHandler(private val x11: ChannelX11) : AbstractSessionIoHandler() {

    private val out get() = x11.out

    override fun sessionClosed(ioSession: IoSession) {
        x11.close(true)
    }

    override fun messageReceived(session: IoSession, message: Readable) {
        val bytes = ByteArray(min(IoUtils.DEFAULT_COPY_SIZE, message.available()))
        if (bytes.isEmpty()) return
        while (message.available() > 0) {
            val available = min(message.available(), bytes.size)
            message.getRawBytes(bytes, 0, available)
            out.write(bytes, 0, available)
        }
        out.flush()
    }

    override fun createSession(ioSession: IoSession): AbstractSession {
        return x11.session as AbstractSession
    }

}