package app.termora.plugin

import app.termora.ApplicationScope
import app.termora.account.AccountManager

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

            // 如果是免费方案，那么不提供此插件的功能
            if (plugin.isPaid()) {
                if (AccountManager.getInstance().isFreePlan()) {
                    continue
                }
            }

            for (extension in plugin.getExtensions(clazz)) {
                if (clazz.isInstance(extension)) {
                    extensions.add(clazz.cast(extension))
                }
            }

        }

        return extensions.sortedBy { it.ordered() }
    }
}