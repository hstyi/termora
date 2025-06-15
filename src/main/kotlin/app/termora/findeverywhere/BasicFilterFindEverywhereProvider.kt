package app.termora.findeverywhere

import app.termora.Scope

class BasicFilterFindEverywhereProvider(private val provider: FindEverywhereProvider) : FindEverywhereProvider {
    override fun find(pattern: String, scope: Scope): List<FindEverywhereResult> {
        val results = provider.find(pattern, scope)
        if (pattern.isBlank()) {
            return results
        }
        return results.filter {
            it.toString().contains(pattern, true)
        }
    }

    override fun order(): Int {
        return provider.order()
    }


    override fun group(): String {
        return provider.group()
    }
}