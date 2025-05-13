package app.termora

import app.termora.plugin.Extension

interface ApplicationRunnerExtension : Extension {
    /**
     * 准备就绪，下一步是启动主窗口
     */
    fun ready()
}