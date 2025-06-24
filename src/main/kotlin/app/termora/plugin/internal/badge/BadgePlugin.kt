package app.termora.plugin.internal.badge

import app.termora.GlassPaneExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class BadgePlugin : InternalPlugin() {

    init {
        support.addExtension(GlassPaneExtension::class.java) { BadgeGlassPaneExtension.instance }
    }

    override fun getName(): String {
        return "Badge"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }
}