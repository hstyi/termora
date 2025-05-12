package app.termora.plugin

import app.termora.Application
import app.termora.ApplicationScope
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FileFilterUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.util.jar.Manifest

class PluginManager private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(PluginManager::class.java)
        private const val ENTRY_NAME = "JANF-Plugin-Entry"

        fun getInstance(): PluginManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(PluginManager::class) { PluginManager() }
        }
    }

    private val plugins = mutableListOf<Plugin>()

    init {
        // load plugins
        loadPlugins()
    }

    /**
     * 获取已经加载的插件
     */
    fun getLoadedPlugins(): List<Plugin> {
        return plugins
    }

    private fun loadPlugins() {
        val pluginsFile = File(Application.getBaseDataDir(), "plugins")
        if (pluginsFile.exists().not()) return
        val dirs = FileUtils.listFilesAndDirs(
            pluginsFile, FileFilterUtils.falseFileFilter(),
            FileFilterUtils.trueFileFilter()
        )
        if (dirs.isEmpty()) return

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

    private fun loadPlugin(file: File) {
        val jars = FileUtils.listFiles(
            file, FileFilterUtils.suffixFileFilter(".jar"),
            FileFilterUtils.falseFileFilter()
        )
        if (jars.isEmpty()) return
        val loader = PluginClassLoader(jars.toTypedArray())
        val resources = loader.findResources("META-INF/MANIFEST.MF").toList()
        if (resources.isEmpty()) return

        val entryNames = linkedSetOf<String>()
        for (e in resources) {
            val entryName = e.openStream().use { Manifest(it).mainAttributes.getValue(ENTRY_NAME) } ?: continue
            entryNames.add(entryName)
        }

        for (name in entryNames) {
            try {
                val clazz = Class.forName(name, false, loader)
                if (clazz.interfaces.contains(Plugin::class.java).not()) continue
                val entry = clazz.getConstructor().newInstance() as Plugin
                plugins.add(entry)
            } catch (e: Throwable) {
                if (log.isErrorEnabled) {
                    log.error("Failed to load plugin entry $name", e)
                }
            }
        }

    }

    private class PluginClassLoader(jarFiles: Array<File>) :
        URLClassLoader(jarFiles.map { it.toURI().toURL() }.toTypedArray())
}