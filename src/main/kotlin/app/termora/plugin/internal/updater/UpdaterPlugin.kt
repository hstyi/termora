package app.termora.plugin.internal.updater

import app.termora.ApplicationRunnerExtension
import app.termora.plugin.Extension
import app.termora.plugin.InternalPlugin

internal class UpdaterPlugin : InternalPlugin() {
    init {
        support.addExtension(ApplicationRunnerExtension::class.java) { MyApplicationRunnerExtension.instance }
    }

    override fun getName(): String {
        return "Updater"
    }


    override fun <T : Extension> getExtensions(clazz: Class<T>): List<T> {
        return support.getExtensions(clazz)
    }


}