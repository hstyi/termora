package app.termora.plugin

import app.termora.Application
import app.termora.ApplicationScope
import app.termora.plugin.internal.local.LocalInternalPlugin
import app.termora.plugin.internal.rdp.RDPInternalPlugin
import app.termora.plugin.internal.serial.SerialInternalPlugin
import app.termora.plugin.internal.sftppty.SFTPPtyInternalPlugin
import app.termora.plugin.internal.ssh.SSHInternalPlugin
import app.termora.sftp.internal.local.LocalPlugin
import app.termora.sftp.internal.sftp.SFTPPlugin
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.util.jar.Manifest

class PluginManager private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(PluginManager::class.java)
        private const val PLUGIN_ENTRY = "TO-Plugin-Entry"
        private const val PLUGIN_RANGE = "TO-Plugin-Range"
        private const val PLUGIN_DIR = "TERMORA_PLUGIN_DIRECTORY"

        fun getInstance(): PluginManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(PluginManager::class) { PluginManager() }
        }
    }

    private val plugins = mutableListOf<Plugin>()

    init {
        // load internal plugins
        loadInternalPlugins()
        // load plugins
        loadPlugins()
    }

    /**
     * 获取已经加载的插件
     */
    fun getLoadedPlugins(): List<Plugin> {
        return plugins
    }

    fun getPluginDirectory(): File {
        val dir = StringUtils.defaultIfBlank(
            System.getProperty(PLUGIN_DIR),
            System.getenv(PLUGIN_DIR)
        )
        if (StringUtils.isNotBlank(dir)) {
            return File(dir)
        }
        return File(Application.getBaseDataDir(), "plugins")
    }

    private fun loadPlugins() {
        val pluginsFile = getPluginDirectory()
        if (log.isInfoEnabled) {
            log.info("Loading plugins ${pluginsFile.absolutePath}")
        }
        if (pluginsFile.exists().not() || pluginsFile.isDirectory.not()) return
        val dirs = pluginsFile.listFiles { file -> file.isDirectory }
        if (ArrayUtils.isEmpty(dirs)) return

        for (file in dirs) {
            try {
                loadPlugin(file)
            } catch (e: Throwable) {
                if (log.isErrorEnabled) {
                    log.error("Failed to load plugin file $file", e)
                }
            }
        }
    }

    private fun loadInternalPlugins() {
        // ssh plugin
        plugins.add(SSHInternalPlugin())
        // serial plugin
        plugins.add(SerialInternalPlugin())
        // local plugin
        plugins.add(LocalInternalPlugin())
        // rdp plugin
        plugins.add(RDPInternalPlugin())
        // sftp pty plugin
        plugins.add(SFTPPtyInternalPlugin())

        // local transfer plugin
        plugins.add(LocalPlugin())
        // sftp transfer plugin
        plugins.add(SFTPPlugin())
    }

    private fun loadPlugin(file: File) {
        val jars = FileUtils.listFiles(
            file, FileFilterUtils.suffixFileFilter(".jar"),
            FileFilterUtils.falseFileFilter()
        )
        if (jars.isEmpty()) return
        val loader = PluginClassLoader(jars.toTypedArray())
        val resources = loader.findResources("META-INF/MANIFEST.MF")

        var pluginEntry = StringUtils.EMPTY
        var pluginRange = StringUtils.EMPTY
        for (e in resources) {
            val attributes = e.openStream().use { Manifest(it).mainAttributes } ?: continue
            pluginEntry = attributes.getValue(PLUGIN_ENTRY) ?: continue
            pluginRange = attributes.getValue(PLUGIN_RANGE) ?: continue
            break
        }

        if (pluginEntry.isBlank() || pluginEntry.isBlank()) return

        try {
            val clazz = Class.forName(pluginEntry, false, loader)
            if (clazz.interfaces.contains(Plugin::class.java).not()) return
            val entry = clazz.getConstructor().newInstance() as Plugin
            plugins.add(entry)
            if (log.isInfoEnabled) {
                log.info("Loaded plugin ${entry.getName()} from $entry")
            }
        } catch (e: Throwable) {
            if (log.isErrorEnabled) {
                log.error("Failed to load plugin entry $pluginEntry", e)
            }
        }

    }

    private class PluginClassLoader(jarFiles: Array<File>) :
        URLClassLoader(jarFiles.map { it.toURI().toURL() }.toTypedArray())
}