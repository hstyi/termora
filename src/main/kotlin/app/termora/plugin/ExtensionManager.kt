package app.termora.plugin

import app.termora.ApplicationScope

class ExtensionManager private constructor() {
    companion object {
        fun getInstance(): ExtensionManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(ExtensionManager::class) { ExtensionManager() }
        }
    }

    fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        val extensions = mutableListOf<T>()
        for (plugin in PluginManager.getInstance().getLoadedPlugins()) {
            for (extension in plugin.getExtensions()) {
                if (clazz.isInstance(extension)) {
                    extensions.add(clazz.cast(extension))
                }
            }
        }
        return extensions
    }
}