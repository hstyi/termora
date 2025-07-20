package app.termora.plugins.vnc

import app.termora.Disposer
import app.termora.Host
import app.termora.HostTerminalTab
import app.termora.WindowScope
import javax.swing.Icon
import javax.swing.JComponent

class VNCTerminalTab(windowScope: WindowScope, host: Host) : HostTerminalTab(windowScope, host) {
    private val viewer = VNCViewer(host)

    override fun getJComponent(): JComponent {
        return viewer
    }

    override fun getIcon(): Icon {
        return VNCProtocolProvider.instance.getIcon()
    }

    override fun canReconnect(): Boolean {
        return false
    }

    override fun dispose() {
        Disposer.dispose(viewer)
    }
}