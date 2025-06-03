package app.termora.plugins.bg

import app.termora.ApplicationRunnerExtension

class BackgroundApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        val instance by lazy { BackgroundApplicationRunnerExtension() }
    }

    override fun ready() {

    }
}