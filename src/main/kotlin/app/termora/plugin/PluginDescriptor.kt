package app.termora.plugin

import java.io.File

class PluginDescriptor(
    val plugin: Plugin,
    val origin: PluginOrigin,
    val version: String,
    val path: File? = null,
)