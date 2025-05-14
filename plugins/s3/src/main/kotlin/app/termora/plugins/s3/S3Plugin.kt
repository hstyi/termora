package app.termora.plugins.s3

import app.termora.Icons
import app.termora.plugin.Extension
import app.termora.plugin.Plugin
import org.apache.commons.lang3.StringUtils
import javax.swing.Icon

class S3Plugin : Plugin {

    override fun getVersion(): String {
        return "1.0.0"
    }

    override fun getAuthor(): String {
        return "TermoraDev"
    }

    override fun getIcon(): Icon {
        return Icons.plugin
    }

    override fun getName(): String {
        return "S3"
    }

    override fun getDescription(): String {
        return StringUtils.EMPTY
    }

    override fun getExtensions(): List<Extension> {
        return listOf(S3ProtocolProviderExtension.instance)
    }

}