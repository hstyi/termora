package app.termora.plugins.s3

import app.termora.DynamicIcon
import app.termora.I18n
import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionSupport
import app.termora.plugin.Plugin
import app.termora.protocol.ProtocolHostPanelExtension
import app.termora.protocol.ProtocolProviderExtension

class S3Plugin : Plugin {
    private val support = ExtensionSupport()

    init {
        support.addExtension(ProtocolProviderExtension::class.java) { S3ProtocolProviderExtension.instance }
        support.addExtension(ProtocolHostPanelExtension::class.java) { S3ProtocolHostPanelExtension.instance }
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): DynamicIcon {
        return Icons.minio
    }

    override fun getName(): String {
        return "S3"
    }

    override fun getDescription(): String {
        return I18n.getString("termora.plugins.s3.description")
    }

    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}