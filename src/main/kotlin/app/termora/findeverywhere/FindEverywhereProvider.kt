package app.termora.findeverywhere

import app.termora.Scope

interface FindEverywhereProvider {

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun getFindEverywhereProviders(scope: Scope): MutableList<FindEverywhereProvider> {
            var list = scope.getAnyOrNull("FindEverywhereProviders")
            if (list == null) {
                list = mutableListOf<FindEverywhereProvider>()
                scope.putAny("FindEverywhereProviders", list)
            }
            return list as MutableList<FindEverywhereProvider>
        }
    }

    /**
     * 搜索
     */
    fun find(pattern: String): List<FindEverywhereResult>

    /**
     * 如果返回非空，表示单独分组
     */
    fun group(): String = "Default Group"

    /**
     * 越小越靠前
     */
    fun order(): Int = Integer.MAX_VALUE
}