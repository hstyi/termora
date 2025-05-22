package app.termora.plugin

import app.termora.Application
import app.termora.ApplicationScope
import app.termora.account.AccountPlugin
import app.termora.plugin.internal.extension.DynamicExtensionPlugin
import app.termora.plugin.internal.local.LocalInternalPlugin
import app.termora.plugin.internal.plugin.PluginInternalPlugin
import app.termora.plugin.internal.rdp.RDPInternalPlugin
import app.termora.plugin.internal.serial.SerialInternalPlugin
import app.termora.plugin.internal.sftppty.SFTPPtyInternalPlugin
import app.termora.plugin.internal.ssh.SSHInternalPlugin
import app.termora.sftp.internal.local.LocalPlugin
import app.termora.sftp.internal.sftp.SFTPPlugin
import app.termora.swingCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.EMPTY
import org.semver4j.Semver
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.util.jar.Attributes
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

    private val plugins = mutableListOf<PluginDescriptor>()

    init {
        // load internal plugins
        loadInternalPlugins()
        // load system plugins
        loadSystemPlugins()
        // load user plugins
        loadPlugins(getPluginDirectory(), PluginOrigin.External)
    }

    /**
     * 获取已经加载的插件
     */
    fun getLoadedPlugins(): List<Plugin> {
        return plugins.map { it.plugin }
    }

    fun getLoadedPluginDescriptor(): Array<PluginDescriptor> {
        return plugins.toTypedArray()
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

    private fun loadPlugins(pluginsFile: File, origin: PluginOrigin) {
        if (log.isInfoEnabled) {
            log.info("Loading plugins ${pluginsFile.absolutePath}")
        }
        if (pluginsFile.exists().not() || pluginsFile.isDirectory.not()) return
        val dirs = pluginsFile.listFiles { file -> file.isDirectory }
        if (ArrayUtils.isEmpty(dirs)) return

        for (file in dirs) {
            try {
                loadPlugin(file, origin)
            } catch (e: Throwable) {
                if (log.isErrorEnabled) {
                    log.error("Failed to load plugin file $file", e)
                }
            }
        }
    }

    private fun loadInternalPlugins() {
        val version = Application.getVersion()

        // 动态注册扩展
        plugins.add(PluginDescriptor(DynamicExtensionPlugin(), PluginOrigin.Internal, version))
        // plugin
        plugins.add(PluginDescriptor(PluginInternalPlugin(), PluginOrigin.Internal, version))
        // account plugin
        plugins.add(PluginDescriptor(AccountPlugin(), PluginOrigin.Internal, version))

        // ssh plugin
        plugins.add(PluginDescriptor(SSHInternalPlugin(), PluginOrigin.Internal, version))
        // serial plugin
        plugins.add(PluginDescriptor(SerialInternalPlugin(), PluginOrigin.Internal, version))
        // local plugin
        plugins.add(PluginDescriptor(LocalInternalPlugin(), PluginOrigin.Internal, version))
        // rdp plugin
        plugins.add(PluginDescriptor(RDPInternalPlugin(), PluginOrigin.Internal, version))
        // sftp pty plugin
        plugins.add(PluginDescriptor(SFTPPtyInternalPlugin(), PluginOrigin.Internal, version))

        // local transfer plugin
        plugins.add(PluginDescriptor(LocalPlugin(), PluginOrigin.Internal, version))
        // sftp transfer plugin
        plugins.add(PluginDescriptor(SFTPPlugin(), PluginOrigin.Internal, version))
    }

    private fun loadSystemPlugins() {
        val appPath = Application.getAppPath()
        if (appPath.isBlank()) return

        val plugins = FileUtils.getFile(appPath, "plugins")
        loadPlugins(plugins, PluginOrigin.System)
    }

    private fun loadPlugin(file: File, origin: PluginOrigin) {
        // 如果有删除标识，那么忽略
        val deletedFile = FileUtils.getFile(file, "deleted")
        if (deletedFile.exists()) {
            swingCoroutineScope.launch(Dispatchers.IO) { FileUtils.deleteQuietly(file) }
            return
        }

        val jars = FileUtils.listFiles(
            file, FileFilterUtils.suffixFileFilter(".jar"),
            FileFilterUtils.falseFileFilter()
        )
        if (jars.isEmpty()) return
        val loader = PluginClassLoader(jars.toTypedArray())
        val resources = loader.findResources("META-INF/MANIFEST.MF")

        var pluginEntry = EMPTY
        var pluginRange = EMPTY
        var pluginVersion = EMPTY
        val version = Semver.parse(Application.getVersion())

        for (e in resources) {
            pluginEntry = EMPTY
            pluginRange = EMPTY
            pluginVersion = EMPTY

            val attributes = e.openStream().use { Manifest(it).mainAttributes } ?: continue
            pluginVersion = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION) ?: continue
            pluginEntry = attributes.getValue(PLUGIN_ENTRY) ?: continue
            pluginRange = attributes.getValue(PLUGIN_RANGE) ?: continue

            if (version != null) {
                if (version.satisfies(pluginRange).not()) {
                    pluginEntry = EMPTY
                    pluginRange = EMPTY
                    pluginVersion = EMPTY
                    if (log.isWarnEnabled) {
                        log.warn("Plugin: ${file.absolutePath} version is not satisfies")
                    }
                    continue
                }
            }
            break
        }

        if (pluginEntry.isBlank() || pluginRange.isBlank() || pluginVersion.isBlank()) return

        try {
            val clazz = Class.forName(pluginEntry, false, loader)
            if (Plugin::class.java.isAssignableFrom(clazz).not()) return
            val entry = clazz.getConstructor().newInstance() as Plugin

            plugins.add(PluginDescriptor(entry, origin, pluginVersion, file))
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