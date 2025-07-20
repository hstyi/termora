package app.termora.plugins.vnc

import com.glavsoft.rfb.protocol.Protocol
import com.glavsoft.viewer.settings.LocalMouseCursorShape
import com.glavsoft.viewer.swing.Surface

class MySurface(protocol: Protocol) : Surface(protocol, 0.5, LocalMouseCursorShape.NO_CURSOR) {

}