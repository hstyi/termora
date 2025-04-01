package app.termora.x11

import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.common.SshConstants
import org.apache.sshd.common.channel.PtyChannelConfigurationHolder
import java.math.BigInteger
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
            buffer.putString(getFakedCookie())
            buffer.putInt(0)
            writePacket(buffer)
        }

        super.doOpenPty()
    }

    private fun getFakedCookie(): String {
        val session = super.getSession()
        var cookie = session.getAttribute(ChannelX11.X11_COOKIE_HEX)
        if (cookie != null) {
            return cookie
        }

        synchronized(session) {
            cookie = session.getAttribute(ChannelX11.X11_COOKIE_HEX)
            if (cookie != null) {
                return cookie
            }

            val foo = Random.nextBytes(16)
            session.setAttribute(ChannelX11.X11_COOKIE, foo)

            cookie = String.format("%032x", BigInteger(1, foo))
            session.setAttribute(ChannelX11.X11_COOKIE_HEX, cookie)

            return cookie
        }
    }
}
