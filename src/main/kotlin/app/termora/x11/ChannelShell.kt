package app.termora.x11

import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.common.SshConstants
import org.apache.sshd.common.channel.PtyChannelConfigurationHolder
import kotlin.random.Random

class ChannelShell(
    configHolder: PtyChannelConfigurationHolder?,
    env: MutableMap<String, *>?
) : ChannelShell(configHolder, env) {

    var xForwarding = false

    override fun doOpenPty() {
        val session = super.getSession()

        if (xForwarding) {
            val buffer = session.createBuffer(SshConstants.SSH_MSG_CHANNEL_REQUEST)
            buffer.putInt(super.getRecipient())
            buffer.putString("x11-req")
            buffer.putBoolean(false) // want-reply
            buffer.putBoolean(false)
            buffer.putString("MIT-MAGIC-COOKIE-1")
            buffer.putBytes(getFakedCookie())
            buffer.putInt(0)
            writePacket(buffer)
        }

        super.doOpenPty()
    }

    private fun getFakedCookie(): ByteArray {
        val session = super.getSession()
        var cookie = ChannelX11.X11_COOKIE_HEX.getOrNull(session)
        if (cookie != null) {
            return cookie as ByteArray
        }

        synchronized(session) {
            cookie = ChannelX11.X11_COOKIE_HEX.getOrNull(session)
            if (cookie != null) {
                return cookie as ByteArray
            }

            val foo = Random.nextBytes(16)
            ChannelX11.X11_COOKIE.set(session, foo)

            val bar = foo.copyOf(32)
            for (i in 0..15) {
                bar[2 * i] = ChannelX11.COOKIE_TABLE[(foo[i].toInt() ushr 4) and 0xf]
                bar[2 * i + 1] = ChannelX11.COOKIE_TABLE[foo[i].toInt() and 0xf]
            }
            ChannelX11.X11_COOKIE_HEX.set(session, bar)

            return bar
        }
    }
}
