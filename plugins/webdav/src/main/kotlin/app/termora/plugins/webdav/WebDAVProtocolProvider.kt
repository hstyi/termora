package app.termora.plugins.webdav

import app.termora.AuthenticationType
import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.ProxyType
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import com.github.sardine.SardineFactory
import okhttp3.Credentials
import org.apache.commons.lang3.StringUtils
import java.io.IOException
import java.net.*


class WebDAVProtocolProvider private constructor() : TransferProtocolProvider {

    companion object {
        val instance by lazy { WebDAVProtocolProvider() }
        const val PROTOCOL = "WebDAV"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.dav
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val host = requester.host

        val sardine = if (host.authentication.type != AuthenticationType.No) {
            if (host.proxy.type != ProxyType.No) {
                SardineFactory.begin(host.username, host.authentication.password, object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> {
                        if (host.proxy.type == ProxyType.HTTP) {
                            return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(host.proxy.host, host.proxy.port)))
                        }
                        return listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress(host.proxy.host, host.proxy.port)))
                    }

                    override fun connectFailed(
                        uri: URI,
                        sa: SocketAddress,
                        ioe: IOException
                    ) {
                        throw ioe
                    }
                })
            } else {
                SardineFactory.begin(host.username, host.authentication.password)
            }
        } else {
            SardineFactory.begin()
        }

        val authorization = if (host.authentication.type != AuthenticationType.No)
            Credentials.basic(host.username, host.authentication.password) else StringUtils.EMPTY
        val defaultPath = host.options.sftpDefaultDirectory
        val fs = WebDAVFileSystem(sardine, StringUtils.removeEnd(host.host, "/"), authorization)
        return PathHandler(fs, fs.getPath(defaultPath))

    }


}