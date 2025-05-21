package app.termora.plugin

import java.io.File

class PluginDescriptor(
    val plugin: Plugin,
    val origin: PluginOrigin,
    val version: String,
    val path: File? = null,

    /**
     * 是否已经卸载
     */
    internal var uninstalled: Boolean = false
)