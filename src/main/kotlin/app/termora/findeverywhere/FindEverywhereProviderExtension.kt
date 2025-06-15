package app.termora.findeverywhere

import app.termora.plugin.Extension

interface FindEverywhereProviderExtension : Extension {
    fun getFindEverywhereProvider(): FindEverywhereProvider
}