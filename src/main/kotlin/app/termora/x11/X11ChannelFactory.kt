package app.termora.x11

import org.apache.sshd.common.channel.Channel
import org.apache.sshd.common.channel.ChannelFactory
import org.apache.sshd.common.session.Session
import org.apache.sshd.core.CoreModuleProperties

class X11ChannelFactory private constructor() : ChannelFactory {
    companion object {
        val INSTANCE = X11ChannelFactory()
    }

    override fun getName(): String {
        return "x11"
    }

    override fun createChannel(session: Session): Channel? {
        val x11Host = CoreModuleProperties.X11_BIND_HOST.getOrNull(session)
        val x11Port = CoreModuleProperties.X11_BASE_PORT.getOrNull(session)
        if (x11Port == null || x11Host == null) return null
        return ChannelX11(x11Host, x11Port)
    }

}