package app.termora.plugin

import app.termora.ApplicationScope
import app.termora.account.AccountManager
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

class ExtensionManager private constructor() {
    companion object {
        fun getInstance(): ExtensionManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(ExtensionManager::class) { ExtensionManager() }
        }
    }

    /**
     * @return 不要缓存结果，因为可能会有动态扩展
     */
    fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        val extensions = mutableListOf<T>()

        for (plugin in PluginManager.getInstance().getLoadedPlugins()) {

            // 如果是免费方案，那么不提供此插件的功能
            if (plugin.isPaid()) {
                if (AccountManager.getInstance().isSigned().not()) {
                    continue
                }
            }

            for (extension in plugin.getExtensions(clazz)) {
                if (clazz.isInstance(extension)) {
                    extensions.add(clazz.cast(proxyExtension(extension)))
                }
            }

        }

        return extensions.sortedBy { it.ordered() }
    }

    fun isExtension(extension: Extension, clazz: KClass<*>): Boolean {
        if (clazz.isInstance(extension)) {
            return true
        }

        if (Proxy.isProxyClass(extension.javaClass)) {
            val invocationHandler = Proxy.getInvocationHandler(extension)
            if (invocationHandler is ExtensionProxy) {
                return clazz.isInstance(invocationHandler.extension())
            }
        }

        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Extension> proxyExtension(extension: T): T {
        return ExtensionProxy(extension).proxyedExtension as T
    }
}