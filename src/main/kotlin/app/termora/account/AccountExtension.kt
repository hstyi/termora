package app.termora.account

import app.termora.plugin.Extension

interface AccountExtension : Extension {
    /**
     * 账户发生变更
     */
    fun onAccountChanged()
}