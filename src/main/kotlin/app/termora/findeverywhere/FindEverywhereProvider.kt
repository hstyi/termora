package app.termora.findeverywhere

import app.termora.Scope
import app.termora.plugin.ExtensionManager

interface FindEverywhereProvider {

    companion object {

        const val SKIP_FIND_EVERYWHERE = "SKIP_FIND_EVERYWHERE"

        fun getFindEverywhereProviders(): List<FindEverywhereProvider> {
            return ExtensionManager.getInstance().getExtensions(FindEverywhereProviderExtension::class.java)
                .map { it.getFindEverywhereProvider() }
        }
    }

    /**
     * 搜索
     */
    fun find(pattern: String, scope: Scope): List<FindEverywhereResult>

    /**
     * 如果返回非空，表示单独分组
     */
    fun group(): String = "Default Group"

    /**
     * 越小越靠前
     */
    fun order(): Int = Integer.MAX_VALUE
}